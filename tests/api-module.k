baseDir = new File(krine.args[0]).getParentFile();
dir = new File(baseDir, "test-module-api");
Module.addModuleSearchPath(dir);

mod = Module.load(this, "hello");
mod.say(mod.hello_world());

mod2 = Module.load(this, "second_hello");
mod2.say(mod2.hello_world());
mod.say(mod.hello_world());
