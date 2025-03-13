package model

abstract class InvalidArgument(message: String, throwable: Throwable) extends Exception(message, throwable)

