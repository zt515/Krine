private String world() {
    return "world_2";
}

private String hello() {
    return "hello_2";
}

public String hello_world() {
    return hello() + " " + world();
}

public void say(String word) {
    println("[Say from second hello]: " + word);
}