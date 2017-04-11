baseDir = new File(krine.args[0]).getParentFile();
dir = new File(baseDir, "test-module-api");

mod = Module.loadFile(this, new File(dir, "hello.k"));
mod.say(mod.helloworld());

