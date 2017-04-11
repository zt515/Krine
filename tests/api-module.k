baseDir = new File(krine.args[0]).getParentFile();
dir = new File(baseDir, "test-module-api");

Module.addModuleSearchPath(dir);
mod = Module.load(this, "hello");
mod.say(mod.helloworld());

