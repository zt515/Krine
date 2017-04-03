double[] x = new double[100];

for (int i = 0; i < x.length; i++) {
    x[i] = i + 1;
}

double sum = 0;
for (double item : x) {
    sum += item;
}

println(sum);
