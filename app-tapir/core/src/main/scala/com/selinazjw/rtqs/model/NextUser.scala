package com.selinazjw.rtqs.model

sealed case class NextUser(exists: Boolean, user: Option[UserPosition])
