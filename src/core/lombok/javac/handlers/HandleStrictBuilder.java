package lombok.javac.handlers;

import static lombok.javac.handlers.JavacHandlerUtil.namePlusTypeParamsToTypeReference;

import java.lang.reflect.Modifier;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import lombok.AccessLevel;
import lombok.StrictBuilder;
import lombok.core.AnnotationValues;
import lombok.core.HandlerPriority;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.spi.Provides;

@Provides @HandlerPriority(65538) public class HandleStrictBuilder extends JavacAnnotationHandler<StrictBuilder> {
	@Override public void handle(AnnotationValues<StrictBuilder> annotation, JCAnnotation ast, JavacNode annotationNode) {
		// アノテーションを削除する
		JavacHandlerUtil.deleteAnnotationIfNeccessary(annotationNode, StrictBuilder.class);

		JavacNode typeNode = annotationNode.up();

		List<JavacNode> fieldNodes = HandleConstructor.findAllFields(typeNode, true);

		if (fieldNodes.size() == 0) {
			return;
		}

		// コンストラクターを作成する
		createConstructor(annotationNode, typeNode, fieldNodes);

		// ビルダークラスを作成する
		JavacNode builderTypeNode = createTargetBuilderClass(typeNode, fieldNodes);

		// フィールド毎のビルダークラスを作成する
		List<JavacNode> fieldBuilderTypeNodes = createFieldBuilderClasses(typeNode, fieldNodes, builderTypeNode);

		// ビルダーファクトリーメソッドを作成する
		createBuilderFactoryMethod(typeNode, fieldBuilderTypeNodes);

	}


	/**
	 * 元クラスのコンストラクターを作成する
	 * 
	 * @param annotationNode
	 * @param typeNode
	 * @param fieldNodes
	 * @return
	 */
	private JCMethodDecl createConstructor(JavacNode annotationNode, JavacNode typeNode, List<JavacNode> fieldNodes) {
		List<JavacNode> params = fieldNodes;
		JCMethodDecl constructor = HandleConstructor.createConstructor(AccessLevel.PACKAGE, List.<JCAnnotation>nil(), typeNode, params, false, annotationNode);
		JavacHandlerUtil.injectMethod(typeNode, constructor);
		return constructor;
	}
	
	/**
	 * 元クラスのビルダークラスを作成する
	 * 
	 * @param parentTypeNode
	 * @param fieldNodes
	 * @return
	 */
	private JavacNode createTargetBuilderClass(JavacNode parentTypeNode, List<JavacNode> parentFieldNodes) {
		JavacTreeMaker parentTreeMaker = parentTypeNode.getTreeMaker();
		
		// ビルダークラスのスケルトンを作成する
		JCClassDecl builderTypeDecl;
		JavacNode builderTypeNode;
		{
			JCModifiers modifiers = parentTreeMaker.Modifiers(Modifier.PUBLIC | Modifier.STATIC);
			Name name = parentTypeNode.toName(parentTypeNode.getName() + "Builder");
			List<JCTypeParameter> params = List.<JCTypeParameter>nil();
			builderTypeDecl = parentTreeMaker.ClassDef(modifiers, name, params, null, List.<JCExpression>nil(), List.<JCTree>nil());
			builderTypeNode = JavacHandlerUtil.injectType(parentTypeNode, builderTypeDecl);
		}
		JavacTreeMaker builderTreeMaker = builderTypeNode.getTreeMaker();

		// フィールドを作成する
		
		for (int i = parentFieldNodes.size() - 1; 0 <= i; i--) { // なぜか逆順に追加されるので順番を反転
			JCVariableDecl sourceFieldDecl = (JCVariableDecl) parentFieldNodes.get(i).get();
			JavacHandlerUtil.injectField(builderTypeNode, sourceFieldDecl);
		}
		List<JavacNode> fieldNodes = parentFieldNodes;
		
		// セッターメソッドを作成する
		for (JavacNode fieldNode : fieldNodes) {
			JCVariableDecl fieldDecl = (JCVariableDecl) fieldNode.get();
			JCModifiers modifiers = builderTreeMaker.Modifiers(0L);
			JCExpression methodType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(builderTreeMaker, builderTypeNode, builderTypeDecl.typarams);
			Name methodName = fieldDecl.getName();
			List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>nil();
			List<JCVariableDecl> methodParameters;
			{
				JCVariableDecl param = builderTreeMaker.VarDef(
					builderTreeMaker.Modifiers(Flags.PARAMETER),
					fieldDecl.getName(),
					JavacHandlerUtil.cloneType(builderTreeMaker, fieldDecl.vartype, builderTypeNode),
					null);
				param.pos = fieldDecl.pos; // これをやらないとUTを実行した時、AssertionErrorが発生する。@see https://stackoverflow.com/questions/46874126/java-lang-assertionerror-thrown-by-compiler-when-adding-generated-method-with-pa
				methodParameters = List.of(param);
			}
			List<JCExpression> methodThrows = List.<JCExpression>nil();
			JCBlock methodBody;
			{
				JCExpression left = JavacHandlerUtil.chainDots(builderTypeNode, "this", fieldNode.getName());
				JCExpression right = builderTreeMaker.Ident(fieldDecl.getName());
				JCExpressionStatement setStatement = builderTreeMaker.Exec(builderTreeMaker.Assign(left, right));
				JCReturn returnStatement = builderTreeMaker.Return(builderTreeMaker.Ident(builderTypeNode.toName("this")));
				methodBody = builderTreeMaker.Block(0, List.<JCStatement>of(setStatement, returnStatement));
			}
			JCMethodDecl method = builderTreeMaker.MethodDef(modifiers, methodName, methodType, methodGenericTypes, methodParameters, methodThrows, methodBody, null);
			JavacHandlerUtil.injectMethod(builderTypeNode, method);
		}

		// buildメソッドを作成する
		{
			JCModifiers modifiers = builderTreeMaker.Modifiers(Modifier.PUBLIC);
			JCExpression methodType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(builderTreeMaker, parentTypeNode, ((JCClassDecl) parentTypeNode.get()).typarams);
			Name methodName = builderTypeNode.toName("build");
			List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>nil();
			List<JCVariableDecl> methodParameters = List.nil();
			List<JCExpression> methodThrows = List.<JCExpression>nil();
			JCBlock methodBody;
			{
				List<JCExpression> args;
				{
					ListBuffer<JCExpression> buffer = new ListBuffer<JCTree.JCExpression>();
					for (JavacNode fieldNode : fieldNodes) {
						JCVariableDecl fieldDecl = (JCVariableDecl) fieldNode.get();
						buffer.append(builderTreeMaker.Ident(fieldDecl.name));
					}
					args = buffer.toList();
				}
				JCReturn returnStatement = builderTreeMaker.Return(builderTreeMaker.NewClass(null, List.<JCExpression>nil(), methodType, args, null));
				methodBody = builderTreeMaker.Block(0, List.<JCStatement>of(returnStatement));
			}
			JCMethodDecl method = builderTreeMaker.MethodDef(modifiers, methodName, methodType, methodGenericTypes, methodParameters, methodThrows, methodBody, null);
			JavacHandlerUtil.injectMethod(builderTypeNode, method);
		}

		return builderTypeNode;
	}

	/**
	 * フィールドのビルダークラスを作成する。
	 * 
	 * @param parentTypeNode
	 * @param fieldNodes
	 */
	private List<JavacNode> createFieldBuilderClasses(JavacNode parentTypeNode, List<JavacNode> fieldNodes, JavacNode builderTypeNode) {

		// フィールドビルダークラスのスケルトンを作成する
		List<JavacNode> fieldBuilderTypeNodes;
		{
			JavacTreeMaker parentTreeMaker = parentTypeNode.getTreeMaker();
			ListBuffer<JavacNode> buffer = new ListBuffer<JavacNode>();
			for (int i = 0; i < fieldNodes.size(); i++) {
				JavacNode fieldNode = fieldNodes.get(i);
				JCModifiers modifiers = parentTreeMaker.Modifiers(Modifier.PUBLIC | Modifier.STATIC);
				Name name = parentTypeNode.toName("$" + toUpperCamelCase(fieldNode.getName() + "FieldBuilder"));
				List<JCTypeParameter> params = List.<JCTypeParameter>nil();
				JCClassDecl fieldBuilderTypeDecl = parentTreeMaker.ClassDef(modifiers, name, params, null, List.<JCExpression>nil(), List.<JCTree>nil());
				JavacNode fieldBuilderTypeNode = JavacHandlerUtil.injectType(parentTypeNode, fieldBuilderTypeDecl);
				buffer.append(fieldBuilderTypeNode);
			}
			fieldBuilderTypeNodes = buffer.toList();
		}

		// フィールドビルダークラスの中身を作成する
		for (int i = 0; i < fieldNodes.size(); i++) {
			JavacNode fieldNode = fieldNodes.get(i);
			JCVariableDecl fieldDecl = (JCVariableDecl) fieldNode.get().clone();
			boolean isFirst = i == 0;
			boolean isLast = i == fieldNodes.size() - 1;
			JavacNode fieldBuilderTypeNode = fieldBuilderTypeNodes.get(i);
			JavacTreeMaker fieldBuilderTreeMaker = fieldBuilderTypeNode.getTreeMaker();
			
			// builderフィールドを作成する
			JavacNode builderFieldNode;
			{
				JCExpression fieldType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(fieldBuilderTreeMaker, builderTypeNode, ((JCClassDecl) builderTypeNode.get()).typarams);
				JCVariableDecl field = fieldBuilderTreeMaker.VarDef(
					fieldBuilderTreeMaker.Modifiers(Flags.PRIVATE),
					fieldNode.toName("builder"),
					fieldType,
					null);
				builderFieldNode = JavacHandlerUtil.injectField(fieldBuilderTypeNode, field);
			}

			// コンストラクターを作成する
			{
				JCMethodDecl constructor;
				if (isFirst) {
					// 最初のフィールドの場合コンストラクターで builder を生成する
					JCModifiers modifiers = fieldBuilderTreeMaker.Modifiers(0L);
					Name methodName = builderTypeNode.toName("<init>");
					List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>nil();
					List<JCVariableDecl> methodParameters = List.nil();
					List<JCExpression> methodThrows = List.<JCExpression>nil();
					JCBlock methodBody;
					{
						JCExpression left = JavacHandlerUtil.chainDots(builderTypeNode, "this", "builder");
						JCExpression right = fieldBuilderTreeMaker.NewClass(
							null,
							List.<JCExpression>nil(),
							namePlusTypeParamsToTypeReference(
								fieldBuilderTreeMaker,
								builderTypeNode,
								((JCClassDecl) builderTypeNode.get()).typarams),
							List.<JCExpression>nil(),
							null);
						JCExpressionStatement setStatement = fieldBuilderTreeMaker.Exec(fieldBuilderTreeMaker.Assign(left, right));
						methodBody = fieldBuilderTreeMaker.Block(0, List.<JCStatement>of(setStatement));
					}
					constructor = fieldBuilderTreeMaker.MethodDef(modifiers, methodName, null, methodGenericTypes, methodParameters, methodThrows, methodBody, null);
				} else {
					List<JavacNode> params = List.of(builderFieldNode);
					constructor = HandleConstructor.createConstructor(AccessLevel.PACKAGE, List.<JCAnnotation>nil(), fieldBuilderTypeNode, params, true, parentTypeNode);
				}
				JavacHandlerUtil.injectMethod(fieldBuilderTypeNode, constructor);
			}
			
			// setterメソッドを作成する
			JavacNode nextFieldBuilderTypeNode = isLast ? null : fieldBuilderTypeNodes.get(i + 1);
			{
				JCModifiers modifiers = fieldBuilderTreeMaker.Modifiers(Modifier.PUBLIC);
				JCExpression methodType;
				if (isLast) {
					methodType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(fieldBuilderTreeMaker, builderTypeNode, ((JCClassDecl) builderTypeNode.get()).typarams);
				} else {
					JCClassDecl nextFieldBuilderTypeDecl = (JCClassDecl) nextFieldBuilderTypeNode.get();
					methodType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(fieldBuilderTreeMaker, nextFieldBuilderTypeNode, nextFieldBuilderTypeDecl.typarams);
				}
				Name methodName = fieldBuilderTypeNode.toName("set" + toUpperCamelCase(fieldNode.getName()));
				List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>nil();
				List<JCVariableDecl> methodParameters;
				{
					JCVariableDecl param = fieldBuilderTreeMaker.VarDef(
						fieldBuilderTreeMaker.Modifiers(Flags.PARAMETER),
						fieldDecl.getName(),
						JavacHandlerUtil.cloneType(fieldBuilderTreeMaker, fieldDecl.vartype, fieldBuilderTypeNode),
						null);
					param.pos = fieldDecl.pos; // これをやらないとUTを実行した時、AssertionErrorが発生する。@see https://stackoverflow.com/questions/46874126/java-lang-assertionerror-thrown-by-compiler-when-adding-generated-method-with-pa
					methodParameters = List.of(param);
				}
				List<JCExpression> methodThrows = List.<JCExpression>nil();
				JCBlock methodBody;
				{
					JCExpression builderSetterMethod = JavacHandlerUtil.chainDots(fieldBuilderTypeNode, "this", "builder", fieldNode.getName());
					List<JCExpression> args = List.<JCExpression>of(fieldBuilderTreeMaker.Ident(fieldDecl.getName()));
					JCMethodInvocation invocation = fieldBuilderTreeMaker.Apply(List.<JCExpression>nil(), builderSetterMethod, args);
					JCReturn returnStatement;
					if (isLast) {
						returnStatement = fieldBuilderTreeMaker.Return(JavacHandlerUtil.chainDots(fieldBuilderTypeNode, "this", "builder"));
					} else {
						JCExpression newNextFieldBuilder = fieldBuilderTreeMaker.NewClass(
							null,
							List.<JCExpression>nil(),
							namePlusTypeParamsToTypeReference(
								fieldBuilderTreeMaker,
								nextFieldBuilderTypeNode,
								((JCClassDecl) nextFieldBuilderTypeNode.get()).typarams),
							List.of(JavacHandlerUtil.chainDots(fieldBuilderTypeNode, "this", "builder")),
							null);
						returnStatement = fieldBuilderTreeMaker.Return(newNextFieldBuilder);
					}
					methodBody = fieldBuilderTreeMaker.Block(0, List.<JCStatement>of(fieldBuilderTreeMaker.Exec(invocation), returnStatement));
				}
				JCMethodDecl method = fieldBuilderTreeMaker.MethodDef(modifiers, methodName, methodType, methodGenericTypes, methodParameters, methodThrows, methodBody, null);
				JavacHandlerUtil.injectMethod(fieldBuilderTypeNode, method);
			}

			// skipメソッドを作成する
			{
				JCModifiers modifiers = fieldBuilderTreeMaker.Modifiers(Modifier.PUBLIC);
				JCExpression methodType;
				if (isLast) {
					methodType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(fieldBuilderTreeMaker, builderTypeNode, ((JCClassDecl) builderTypeNode.get()).typarams);
				} else {
					JCClassDecl nextFieldBuilderTypeDecl = (JCClassDecl) nextFieldBuilderTypeNode.get();
					methodType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(fieldBuilderTreeMaker, nextFieldBuilderTypeNode, nextFieldBuilderTypeDecl.typarams);
				}
				Name methodName = fieldBuilderTypeNode.toName("skip" + toUpperCamelCase(fieldNode.getName()));
				List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>nil();
				List<JCVariableDecl> methodParameters = List.nil();
				List<JCExpression> methodThrows = List.<JCExpression>nil();
				JCBlock methodBody;
				{
					JCReturn returnStatement;
					if (isLast) {
						returnStatement = fieldBuilderTreeMaker.Return(JavacHandlerUtil.chainDots(fieldBuilderTypeNode, "this", "builder"));
					} else {
						JCExpression newNextFieldBuilder = fieldBuilderTreeMaker.NewClass(
							null,
							List.<JCExpression>nil(),
							namePlusTypeParamsToTypeReference(
								fieldBuilderTreeMaker,
								nextFieldBuilderTypeNode,
								((JCClassDecl) nextFieldBuilderTypeNode.get()).typarams),
							List.of(JavacHandlerUtil.chainDots(fieldBuilderTypeNode, "this", "builder")),
							null);
						returnStatement = fieldBuilderTreeMaker.Return(newNextFieldBuilder);
					}
					methodBody = fieldBuilderTreeMaker.Block(0, List.<JCStatement>of(returnStatement));
				}
				JCMethodDecl method = fieldBuilderTreeMaker.MethodDef(modifiers, methodName, methodType, methodGenericTypes, methodParameters, methodThrows, methodBody, null);
				JavacHandlerUtil.injectMethod(fieldBuilderTypeNode, method);
			}
		}
		
		return fieldBuilderTypeNodes;
	}

	/**
	 * 最初のビルダーを作成するスタティックメソッドを作成する
	 * 
	 * @param typeNode
	 * @param fieldBuilderTypeNodes
	 */
	private void createBuilderFactoryMethod(JavacNode typeNode, List<JavacNode> fieldBuilderTypeNodes) {
		JavacTreeMaker typeTreeMaker = typeNode.getTreeMaker();
		JavacNode firstFieldBuilderTypeNode = fieldBuilderTypeNodes.get(0);
		// builderメソッドを作成する
		{
			JCModifiers modifiers = typeTreeMaker.Modifiers(Modifier.PUBLIC | Modifier.STATIC);
			JCExpression methodType = JavacHandlerUtil.namePlusTypeParamsToTypeReference(typeTreeMaker, firstFieldBuilderTypeNode, ((JCClassDecl) firstFieldBuilderTypeNode.get()).typarams);
			Name methodName = typeNode.toName("builder");
			List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>nil();
			List<JCVariableDecl> methodParameters = List.nil();
			List<JCExpression> methodThrows = List.<JCExpression>nil();
			JCBlock methodBody;
			{
				List<JCExpression> args = List.nil();
				JCReturn returnStatement = typeTreeMaker.Return(typeTreeMaker.NewClass(null, List.<JCExpression>nil(), methodType, args, null));
				methodBody = typeTreeMaker.Block(0, List.<JCStatement>of(returnStatement));
			}
			JCMethodDecl method = typeTreeMaker.MethodDef(modifiers, methodName, methodType, methodGenericTypes, methodParameters, methodThrows, methodBody, null);
			JavacHandlerUtil.injectMethod(typeNode, method);
		}
	}

	private static String toUpperCamelCase(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		String first = s.substring(0, 1).toUpperCase();
		String remains = s.length() == 1 ? "" : s.substring(1);
		return first + remains;
	}
}
