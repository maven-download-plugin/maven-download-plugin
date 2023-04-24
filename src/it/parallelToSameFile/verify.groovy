File f = new File(basedir, "target/hello-1.0.pom")

assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"
