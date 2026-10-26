package com.addonstation.mobile.data

import java.io.IOException

/** أنواع الأخطاء الموحّدة في التطبيق، تُترجم إلى نصوص من موارد اللغة. */
enum class ApiErrorKind {
    NOT_CONFIGURED,
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    ALREADY_REGISTERED,
    PASSWORD_TOO_SHORT,
    INVALID_EMAIL,
    SERVER,
    NETWORK,
    NOT_SIGNED_IN,
    NOT_ADMIN,
    NO_PERMISSION,
    NOT_ENOUGH_AC,
    DOWNLOAD_LIMIT,
    FILE_NOT_UPLOADED,
    NOT_FOUND,
    PLAN_UNAVAILABLE,
    METHOD_DISABLED,
    TOO_MANY_ORDERS,
    ALREADY_CLAIMED,
    ALREADY_UNLOCKED,
    INVALID_STATUS,
    MUTED_OR_BANNED,
    DELETE_WINDOW_EXPIRED,
    MEDIA_ADMIN_ONLY,
    ALREADY_DELETED,
    RULES_REQUIRED,
    INVALID_IMAGE_FORMAT,
    IMAGE_TOO_LARGE,
    MEDIA_TOO_LARGE,
    GENERIC,
}

class ApiError(
    val kind: ApiErrorKind,
    val raw: String? = null,
    val status: Int = 0,
) : Exception(raw ?: kind.name)

/** يحوّل رسالة الخادم النصية إلى نوع خطأ معروف. */
fun apiErrorFromMessage(message: String?, status: Int = 0): ApiError {
    val m = message.orEmpty().lowercase()
    val kind = when {
        m.contains("invalid login credentials") -> ApiErrorKind.INVALID_CREDENTIALS
        m.contains("email not confirmed") -> ApiErrorKind.EMAIL_NOT_CONFIRMED
        m.contains("user already registered") -> ApiErrorKind.ALREADY_REGISTERED
        m.contains("already registered") -> ApiErrorKind.ALREADY_REGISTERED
        m.contains("password should be at least") -> ApiErrorKind.PASSWORD_TOO_SHORT
        m.contains("invalid format") || m.contains("unable to validate email") -> ApiErrorKind.INVALID_EMAIL
        m.contains("sign_in_required") -> ApiErrorKind.NOT_SIGNED_IN
        m.contains("not_admin") -> ApiErrorKind.NOT_ADMIN
        m.contains("no_permission") || m.contains("forbidden") -> ApiErrorKind.NO_PERMISSION
        m.contains("not_enough_ac") -> ApiErrorKind.NOT_ENOUGH_AC
        m.contains("download_limit") -> ApiErrorKind.DOWNLOAD_LIMIT
        m.contains("file_not_uploaded") -> ApiErrorKind.FILE_NOT_UPLOADED
        m.contains("not_found") -> ApiErrorKind.NOT_FOUND
        m.contains("plan_unavailable") -> ApiErrorKind.PLAN_UNAVAILABLE
        m.contains("method_disabled") || m.contains("method_unavailable") -> ApiErrorKind.METHOD_DISABLED
        m.contains("too_many_open_orders") -> ApiErrorKind.TOO_MANY_ORDERS
        m.contains("already_claimed") -> ApiErrorKind.ALREADY_CLAIMED
        m.contains("already_unlocked") || m.contains("not_priced") -> ApiErrorKind.ALREADY_UNLOCKED
        m.contains("invalid_status") -> ApiErrorKind.INVALID_STATUS
        m.contains("admin_only_media") -> ApiErrorKind.MEDIA_ADMIN_ONLY
        m.contains("delete_window_expired") -> ApiErrorKind.DELETE_WINDOW_EXPIRED
        m.contains("already_deleted") -> ApiErrorKind.ALREADY_DELETED
        m.contains("muted") || m.contains("banned") -> ApiErrorKind.MUTED_OR_BANNED
        m.contains("rules_required") || m.contains("accept_rules") -> ApiErrorKind.RULES_REQUIRED
        m.contains("image_format") -> ApiErrorKind.INVALID_IMAGE_FORMAT
        m.contains("image_too_large") -> ApiErrorKind.IMAGE_TOO_LARGE
        m.contains("media_too_large") -> ApiErrorKind.MEDIA_TOO_LARGE
        status == 401 -> ApiErrorKind.NOT_SIGNED_IN
        status >= 500 -> ApiErrorKind.SERVER
        else -> ApiErrorKind.GENERIC
    }
    return ApiError(kind, message, status)
}

fun Throwable.toApiError(): ApiError = when (this) {
    is ApiError -> this
    is IOException -> ApiError(ApiErrorKind.NETWORK, message)
    else -> ApiError(ApiErrorKind.GENERIC, message)
}
