File f = new File(basedir, "target/ant-contrib-1.0b2-bin.zip")
assert !f.exists()
f = new File(basedir, "target/ant-contrib/README.txt")
assert f.exists()
assert f.length() > 0
