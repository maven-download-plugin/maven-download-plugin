def f = new File(basedir, "target/apache-maven-3.8.7.zip")
assert !f.exists()
f = new File(basedir, "target/apache-maven-3.8.7/README.txt")
assert f.exists()
assert f.length() > 0
