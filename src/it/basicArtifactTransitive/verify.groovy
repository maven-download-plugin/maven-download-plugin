def f = new File(basedir, 'target/javax.annotation-api-1.3.2.jar')
assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : 'File is empty'
