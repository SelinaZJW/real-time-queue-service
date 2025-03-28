package com.selinazjw.rtqs.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed case class ErrorResponse(code: Int, message: String)

object ErrorResponse {
  given Codec[ErrorResponse] = deriveCodec
}