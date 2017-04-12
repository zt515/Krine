Module.export(this, "hello");

private String world() {
    return "world";
}

public String hello() {
    return "hello";
}

public String hello_world() {
    return hello() + " " + world();
}

public void say(String word) {
    println("[Say]: " + word);
    methodInMain();
}

