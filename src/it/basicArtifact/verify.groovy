def f = new File(basedir, 'target/pom.xml')
assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : 'File is empty'
