baseDir = new File(krine.args[0]).getParentFile();
dir = new File(baseDir, "test-karfile");
kar = new File(dir, "one_two_three.kar");

Module.addModuleSearchPath(kar);

one = Module.load(this, "one");
two = Module.load(this, "two");
three = Module.load(this, "three");

one.f();
two.f();
three.f();
