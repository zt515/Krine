Module.export(this, "hello");

private String world() {
    return "world";
}

public String hello() {
    return "hello";
}

public String helloworld() {
    return hello() + " " + world();
}

public void say(String word) {
    println("[Say]: " + word);
}

