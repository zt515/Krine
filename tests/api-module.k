baseDir = new File(krine.args[0]).getParentFile();
dir = new File(baseDir, "test-module-api");

private void h() {
    println("233");
}

Module.addModuleSearchPath(dir);
mod = Module.load(this, "hello");
mod.say(mod.hello_world());
