package com.addonstation.mobile.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * مستقبل رسائل FCM.
 *
 * لماذا data-only؟ لأننا نرسل من الخادم حقلاً `data` بلا `notification`،
 * فيصل كل شيء إلى هنا **حتى والتطبيق مغلق**، ونحن الذين نبني الإشعار:
 *   • النص بلغة الجهاز (يترجمه الخادم لكل جهاز بلغته).
 *   • القناة الصحيحة (شات / محتوى / اجتماعي / حساب / تذكير).
 *   • الفتح على الشاشة المطلوبة (route) لا على الشاشة الرئيسية دائماً.
 *
 * إن كانت FCM غير مُهيّأة في هذا البناء (لا يوجد google-services.json)، لا
 * تُستدعى هذه الخدمة أصلاً — فلا شيء يُنفَّذ ولا خطر.
 */
class PushService : FirebaseMessagingService() {

    /** توكن جديد (أول تشغيل · إعادة تثبيت · مسح بيانات التطبيق). */
    override fun onNewToken(token: String) {
        PushRegistrar.sendTokenToServer(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isNotEmpty()) {
            PushNotifications.show(applicationContext, message.data)
        }
    }
}
