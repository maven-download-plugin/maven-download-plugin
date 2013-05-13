/**
 * Copyright 2012, Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode;

import java.io.Serializable;

/**
 * This describes a cache file
 * @author Mickael Istria (Red Hat Inc)
 *
 */
class CachedFileEntry implements Serializable {
	private static final long serialVersionUID = 322094691022939391L;

	public String fileName;

	/**
	 * @deprecated As Entries can be updated externally and become
	 * inconsistent, we mustnt store signatures and always need to
	 * re-compute them
	 */
	@Deprecated
	public String md5;
	@Deprecated
	public String sha1;
}