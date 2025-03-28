package com.selinazjw.rtqs.model

sealed case class InvalidArgument(message: String) extends Exception(message)