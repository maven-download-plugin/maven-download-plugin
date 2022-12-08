def f = new File(basedir, 'target/target-pom.xml')
assert f.exists() : "File $f.absolutePath does not exist"
assert f.text.contains('<artifactId>dummy-api</artifactId>')
