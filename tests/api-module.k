baseDir = new File(krine.args[0]).getParentFile();
dir = new File(baseDir, "test-module-api");

void methodInMain() {
    println("method in main");
}

Module.addModuleSearchPath(dir);
mod = Module.load(this, "hello");
mod.say(mod.hello_world());
