def resource = new File(basedir, "output.txt")
assert resource.exists() : "$resource does not exist"
assert resource.text =~ /hello/ : "$resource does not contain 'hello'"
assert ! new File(basedir).listFiles({it.directory} as FileFilter) : "Expected no directories to be created"
