_() {
    Module.export(this, "test_module");

    public void say(String word) {
        println("[Say]: " + word);
    }
}

_();
mod = Module.load(this, "test_module");
mod.say("hello world");

