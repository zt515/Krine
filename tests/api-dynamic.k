m = new Dynamic.MethodBuilder()
    .withReturnType(Integer.class)
    .addParameter("x", Integer.class)
    .withName("getInt")
    .withCode("return x + 100;")
    .withAccessibility(DynamicMethod.Accessibility.PUBLIC)
    .build();

Dynamic.defineMethod(this, m);

println(getInt(33));
