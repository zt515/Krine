import krine.dynamic.DynamicMethod;

DynamicMethod m = new DynamicMethod.Builder()
    .withReturnType(Integer.class)
    .withName("getInt")
    .withCode("return 100;")
    .withAccessibility(DynamicMethod.Accessibility.PUBLIC)
    .build();

krine.dynamic.loadDynamicMethod(m);

println(getInt());
