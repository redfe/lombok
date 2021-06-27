public class StrictBuilder {
	private String name;
	private Integer age = 1;
	private String phoneNumber;

	@java.lang.SuppressWarnings("all")
	StrictBuilder(final String name, final Integer age, final String phoneNumber) {
		this.name = name;
		this.age = age;
		this.phoneNumber = phoneNumber;
	}


	@java.lang.SuppressWarnings("all")
	public static class StrictBuilderBuilder {
		private String name;
		private Integer age = 1;
		private String phoneNumber;

		@java.lang.SuppressWarnings("all")
		StrictBuilder.StrictBuilderBuilder name(String name) {
			this.name = name;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		StrictBuilder.StrictBuilderBuilder age(Integer age) {
			this.age = age;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		StrictBuilder.StrictBuilderBuilder phoneNumber(String phoneNumber) {
			this.phoneNumber = phoneNumber;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		public StrictBuilder build() {
			return new StrictBuilder(name, age, phoneNumber);
		}
	}


	@java.lang.SuppressWarnings("all")
	public static class $NameFieldBuilder {
		private StrictBuilder.StrictBuilderBuilder builder;

		@java.lang.SuppressWarnings("all")
		$NameFieldBuilder() {
			this.builder = new StrictBuilder.StrictBuilderBuilder();
		}

		@java.lang.SuppressWarnings("all")
		public StrictBuilder.$AgeFieldBuilder setName(String name) {
			this.builder.name(name);
			return new StrictBuilder.$AgeFieldBuilder(this.builder);
		}

		@java.lang.SuppressWarnings("all")
		public StrictBuilder.$AgeFieldBuilder skipName() {
			return new StrictBuilder.$AgeFieldBuilder(this.builder);
		}
	}


	@java.lang.SuppressWarnings("all")
	public static class $AgeFieldBuilder {
		private StrictBuilder.StrictBuilderBuilder builder;

		@java.lang.SuppressWarnings("all")
		$AgeFieldBuilder(final StrictBuilder.StrictBuilderBuilder builder) {
			this.builder = builder;
		}

		@java.lang.SuppressWarnings("all")
		public StrictBuilder.$PhoneNumberFieldBuilder setAge(Integer age) {
			this.builder.age(age);
			return new StrictBuilder.$PhoneNumberFieldBuilder(this.builder);
		}

		@java.lang.SuppressWarnings("all")
		public StrictBuilder.$PhoneNumberFieldBuilder skipAge() {
			return new StrictBuilder.$PhoneNumberFieldBuilder(this.builder);
		}
	}

	@java.lang.SuppressWarnings("all")
	public static class $PhoneNumberFieldBuilder {
		private StrictBuilder.StrictBuilderBuilder builder;

		@java.lang.SuppressWarnings("all")
		$PhoneNumberFieldBuilder(final StrictBuilder.StrictBuilderBuilder builder) {
			this.builder = builder;
		}

		@java.lang.SuppressWarnings("all")
		public StrictBuilder.StrictBuilderBuilder setPhoneNumber(String phoneNumber) {
			this.builder.phoneNumber(phoneNumber);
			return this.builder;
		}

		@java.lang.SuppressWarnings("all")
		public StrictBuilder.StrictBuilderBuilder skipPhoneNumber() {
			return this.builder;
		}
	}

	@java.lang.SuppressWarnings("all")
	public static StrictBuilder.$NameFieldBuilder builder() {
		return new StrictBuilder.$NameFieldBuilder();
	}
}
