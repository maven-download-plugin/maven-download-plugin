def f = new File(basedir, "target/apache-maven-3.8.6.zip")
assert !f.exists()
f = new File(basedir, "target/apache-maven-3.8.6/README.txt")
assert f.exists()
assert f.length() > 0
