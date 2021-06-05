package net.sergeych.kotyara

open class DbException(message: String = "kotyara.Exception", cause: Throwable?=null) : Exception(message, cause)

class NoMoreConnectionsException(message: String?=null) : DbException(message ?: "connection pool exhausted")

class NotFoundException(message: String = "not found"):  DbException(message)

class NotInitializedException(message: String = "database is not initialized"):  DbException(message)