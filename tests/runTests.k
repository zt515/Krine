println("Krine Tests Runner");
println("------------------");

self = new File(krine.args[0]);
testsDir = self.getParentFile();

args = new String[1];
for (file : testsDir.listFiles()) {
    if (!file.isFile() || file.getName().equals(self.getName())) {
        continue;
    }

    println("=> Running " + file.getName());
    args[0] = file.getAbsolutePath();
    com.krine.command.Main.main(args);
    println("");
}
