import krine.dynamic.DynamicMethod;

DynamicMethod m = new DynamicMethod.Builder()
    .withReturnType(Integer.class)
    .addParameter("x", Integer.class)
    .withName("getInt")
    .withCode("return x + 100;")
    .withAccessibility(DynamicMethod.Accessibility.PUBLIC)
    .build();

krine.dynamic.loadDynamicMethod(m);

println(getInt(33));
