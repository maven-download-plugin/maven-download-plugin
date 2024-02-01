def resource = new File(basedir, "target/output.txt")
assert resource.exists() : "File $f.absolutePath does not exist"
assert resource.text =~ /dummy-api/ : "The contents of the file is incorrect"
