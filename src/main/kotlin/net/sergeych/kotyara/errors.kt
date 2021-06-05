package net.sergeych.kotyara

open class DbException(message: String = "kotyara.Exception", cause: Throwable?=null) : Exception(message, cause)

open class NoMoreConnectionsException(message: String?=null) : DbException(message ?: "connection pool exhausted")
class DatabasePausedException() : NoMoreConnectionsException("Database is paused")

class DatabaseIsClosedException() : DbException("Database is closed")

class NotFoundException(message: String = "not found"):  DbException(message)

class NotInitializedException(message: String = "database is not initialized"):  DbException(message)