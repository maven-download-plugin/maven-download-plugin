def resource = new File(basedir, "target/output.txt")
assert resource.exists() : "File does not exist"
assert resource.text =~ /hello/ : "The contents of the file is incorrect"
