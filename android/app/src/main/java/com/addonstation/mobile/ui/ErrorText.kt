package com.addonstation.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiErrorKind

/** يحوّل نوع الخطأ إلى نص من موارد اللغة الحالية. */
@Composable
fun apiErrorText(kind: ApiErrorKind): String = when (kind) {
    ApiErrorKind.NOT_CONFIGURED -> stringResource(R.string.setup_error)
    ApiErrorKind.INVALID_CREDENTIALS -> stringResource(R.string.msg_invalid_credentials)
    ApiErrorKind.EMAIL_NOT_CONFIRMED -> stringResource(R.string.msg_email_not_confirmed)
    ApiErrorKind.ALREADY_REGISTERED -> stringResource(R.string.msg_already_registered)
    ApiErrorKind.PASSWORD_TOO_SHORT -> stringResource(R.string.msg_password_too_short)
    ApiErrorKind.INVALID_EMAIL -> stringResource(R.string.msg_invalid_email)
    ApiErrorKind.SERVER -> stringResource(R.string.msg_server_error)
    ApiErrorKind.NETWORK -> stringResource(R.string.msg_network_error)
    ApiErrorKind.NOT_SIGNED_IN -> stringResource(R.string.msg_sign_in_needed)
    ApiErrorKind.NOT_ADMIN -> stringResource(R.string.admin_not_allowed)
    ApiErrorKind.NO_PERMISSION -> stringResource(R.string.chat_no_links)
    ApiErrorKind.NOT_ENOUGH_AC -> stringResource(R.string.ac_not_enough)
    ApiErrorKind.DOWNLOAD_LIMIT -> stringResource(R.string.plan_download_limit)
    ApiErrorKind.FILE_NOT_UPLOADED -> stringResource(R.string.item_file_missing)
    ApiErrorKind.NOT_FOUND -> stringResource(R.string.item_not_found)
    ApiErrorKind.PLAN_UNAVAILABLE -> stringResource(R.string.plan_unavailable)
    ApiErrorKind.METHOD_DISABLED -> stringResource(R.string.plan_payment_note)
    ApiErrorKind.TOO_MANY_ORDERS -> stringResource(R.string.msg_generic_error)
    ApiErrorKind.ALREADY_CLAIMED -> stringResource(R.string.ac_claimed)
    ApiErrorKind.ALREADY_UNLOCKED -> stringResource(R.string.ac_unlocked)
    ApiErrorKind.INVALID_STATUS -> stringResource(R.string.msg_generic_error)
    ApiErrorKind.DELETE_WINDOW_EXPIRED -> stringResource(R.string.chat_delete_expired)
    ApiErrorKind.ALREADY_DELETED -> stringResource(R.string.chat_delete_done)
    ApiErrorKind.MUTED_OR_BANNED -> stringResource(R.string.chat_muted_until, "—")
    ApiErrorKind.RULES_REQUIRED -> stringResource(R.string.chat_rules_title)
    ApiErrorKind.INVALID_IMAGE_FORMAT -> stringResource(R.string.chat_image_formats)
    ApiErrorKind.IMAGE_TOO_LARGE -> stringResource(R.string.chat_image_max)
    ApiErrorKind.MEDIA_TOO_LARGE -> stringResource(R.string.chat_media_max)
    ApiErrorKind.GENERIC -> stringResource(R.string.msg_generic_error)
}
