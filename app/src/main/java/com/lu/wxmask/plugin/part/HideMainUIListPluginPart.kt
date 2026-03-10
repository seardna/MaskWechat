package com.lu.wxmask.plugin.part

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import com.lu.lposed.api2.XC_MethodHook2
import com.lu.lposed.api2.XposedHelpers2
import com.lu.lposed.plugin.IPlugin
import com.lu.magic.util.ResUtil
import com.lu.magic.util.log.LogUtil
import com.lu.magic.util.view.ChildDeepCheck
import com.lu.wxmask.ClazzN
import com.lu.wxmask.Constrant
import com.lu.wxmask.MainHook
import com.lu.wxmask.plugin.WXMaskPlugin
import com.lu.wxmask.util.AppVersionUtil
import com.lu.wxmask.util.ConfigUtil
import com.lu.wxmask.util.ext.getViewId
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 主页UI处理：彻底解决冷启动失效、点击乱跳，并新增自主选择隐藏聊天栏功能
 */
class HideMainUIListPluginPart : IPlugin {

    // =========================================================================
    // 【全新功能配置开关】
    // 你可以根据需求，将这个变量改为 true，开启“主页完全隐藏模式”。
    // 开启后：主页聊天栏完全消失，但在主页底部的“微信”Tab依然会有红点提示来信。
    // (如果你在 App 中有对应的开关配置，可以替换为 option.yourConfigSwitch)
    // =========================================================================
    private val completelyHideMainUI = false 

    // 全自动官方号盲盒系统
    private fun getAutoTarget(realWxid: String): Pair<String, String> {
        val officialAccounts = listOf(
            Pair("weixin", "微信团队"),
            Pair("officialaccounts", "订阅号消息"),
            Pair("gh_43f2581f6fd6", "微信运动"),
            Pair("filehelper", "文件传输助手"),
            Pair("wxpayapp", "微信支付"),
            Pair("notifymessage", "服务通知")
        )
        val index = Math.abs(realWxid.hashCode()) % officialAccounts.size
        return officialAccounts[index]
    }

    val GetItemMethodName = when (AppVersionUtil.getVersionCode()) {
        Constrant.WX_CODE_8_0_22 -> "aCW"
        in Constrant.WX_CODE_8_0_22..Constrant.WX_CODE_8_0_43 -> "k" 
        Constrant.WX_CODE_PLAY_8_0_48 -> "l"
        Constrant.WX_CODE_8_0_49, Constrant.WX_CODE_8_0_51, Constrant.WX_CODE_8_0_56 -> "l"
        Constrant.WX_CODE_8_0_50 -> "n"
        Constrant.WX_CODE_8_0_53 -> "m"
        else -> "m"
    }

    override fun handleHook(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        // 由于采用了全新的强力拦截模式，去除了繁杂脆弱的 hookListViewClick，集中在 getView 处理。
        runCatching {
            handleMainUIChattingListView2(context, lpparam)
        }.onFailure {
            LogUtil.w("hide mainUI listview fail, try to old function.")
            handleMainUIChattingListView(context, lpparam)
        }
    }

    private fun handleMainUIChattingListView(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        val adapterName = when (AppVersionUtil.getVersionCode()) {
            Constrant.WX_CODE_8_0_22 -> "com.tencent.mm.ui.conversation.k"
            in Constrant.WX_CODE_8_0_32..Constrant.WX_CODE_8_0_34 -> {
                if (AppVersionUtil.getVersionName() == "8.0.35") "com.tencent.mm.ui.conversation.r" else "com.tencent.mm.ui.conversation.p"
            }
            Constrant.WX_CODE_8_0_35 -> "com.tencent.mm.ui.conversation.r"
            in Constrant.WX_CODE_8_0_35..Constrant.WX_CODE_8_0_41 -> "com.tencent.mm.ui.conversation.x" 
            Constrant.WX_CODE_8_0_47 -> "com.tencent.mm.ui.conversation.p3"
            Constrant.WX_CODE_8_0_50 -> "com.tencent.mm.ui.conversation.q3"
            else -> null
        }
        var adapterClazz: Class<*>? = null
        if (adapterName != null) {
            adapterClazz = ClazzN.from(adapterName, context.classLoader)
        }
        if (adapterClazz != null) {
            hookListViewAdapter(adapterClazz)
        } else {
            val setAdapterMethod = XposedHelpers2.findMethodExactIfExists(
                ListView::class.java.name,
                context.classLoader,
                "setAdapter",
                ListAdapter::class.java
            )
            if (setAdapterMethod == null) return
            XposedHelpers2.hookMethod(
                setAdapterMethod,
                object : XC_MethodHook2() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val adapter = param.args[0] ?: return
                        if (adapter::class.java.name.startsWith("com.tencent.mm.ui.conversation")) {
                            hookListViewAdapter(adapter.javaClass)
                        }
                    }
                }
            )
        }
    }

    private fun handleMainUIChattingListView2(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        val adapterClazzName = when (AppVersionUtil.getVersionCode()) {
            Constrant.WX_CODE_8_0_22 -> "com.tencent.mm.ui.g"
            in Constrant.WX_CODE_8_0_32..Constrant.WX_CODE_8_0_34 -> "com.tencent.mm.ui.y"
            in Constrant.WX_CODE_8_0_35..Constrant.WX_CODE_8_0_38 -> "com.tencent.mm.ui.z"
            in Constrant.WX_CODE_8_0_40..Constrant.WX_CODE_8_0_43 -> "com.tencent.mm.ui.b0"
            in Constrant.WX_CODE_8_0_43..Constrant.WX_CODE_8_0_44 -> "com.tencent.mm.ui.h3"
            in Constrant.WX_CODE_8_0_43..Constrant.WX_CODE_8_0_47,
            Constrant.WX_CODE_PLAY_8_0_48, Constrant.WX_CODE_8_0_50, Constrant.WX_CODE_8_0_51, Constrant.WX_CODE_8_0_53, Constrant.WX_CODE_8_0_56 -> "com.tencent.mm.ui.i3"
            in Constrant.WX_CODE_8_0_58..Constrant.WX_CODE_8_0_60 -> "com.tencent.mm.ui.k3"
            Constrant.WX_CODE_8_0_69 -> "o75.v0" 
            else -> null
        }
        
        var adapterClazz = if (adapterClazzName != null) ClazzN.from(adapterClazzName) else null

        if (adapterClazz != null) {
            var getItemMethod = findGetItemMethod(adapterClazz)
            if (getItemMethod != null) {
                hookListViewGetItem(getItemMethod)
            }
            hookListViewAdapter(adapterClazz)
            return
        }

        XposedHelpers2.findAndHookMethod(
            ListView::class.java,
            "setAdapter",
            ListAdapter::class.java,
            object : XC_MethodHook2() {
                private var isHookGetItemMethod = false
                override fun afterHookedMethod(param: MethodHookParam) {
                    val adapter = param.args[0] ?: return
                    if (adapter::class.java.name.startsWith("com.tencent.mm.ui.conversation")) {
                        if (isHookGetItemMethod) return
                        var getItemMethod = findGetItemMethod(adapter::class.java.superclass)
                        if (getItemMethod == null) {
                            getItemMethod = XposedHelpers2.findMethodExactIfExists(adapter::class.java.superclass, "getItem", Integer.TYPE)
                        }
                        if (getItemMethod != null) {
                            hookListViewGetItem(getItemMethod)
                            isHookGetItemMethod = true
                        }
                    }
                }
            }
        )
    }

    private fun hookListViewGetItem(getItemMethod: Method) {
        XposedHelpers2.hookMethod(
            getItemMethod,
            object : XC_MethodHook2() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val itemData: Any = param.result ?: return
                    val chatUser: String? = XposedHelpers2.getObjectField(itemData, "field_username") as? String
                    if (chatUser == null) return

                    if (WXMaskPlugin.containChatUser(chatUser)) {
                        val option = ConfigUtil.getOptionData()
                        // 净化数据层：彻底抹除预览文字、红点等，但绝不更改底层 ID，保障正常删除！
                        XposedHelpers2.setObjectField(itemData, "field_content", "")
                        XposedHelpers2.setObjectField(itemData, "field_digest", "")
                        XposedHelpers2.setObjectField(itemData, "field_unReadCount", 0)
                        XposedHelpers2.setObjectField(itemData, "field_UnReadInvite", 0)
                        XposedHelpers2.setObjectField(itemData, "field_unReadMuteCount", 0)
                        XposedHelpers2.setObjectField(itemData, "field_msgType", "1")

                        if (option.enableTravelTime && option.travelTime != 0L) {
                            val cTime = XposedHelpers2.getObjectField<Any>(itemData, "field_conversationTime")
                            if (cTime is Long) {
                                XposedHelpers2.setObjectField(itemData, "field_conversationTime", cTime - option.travelTime)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun hookListViewAdapter(adapterClazz: Class<*>) {
        var getViewMethod: Method? = null
        var currentClass: Class<*>? = adapterClazz
        while (currentClass != null && currentClass != Any::class.java) {
            getViewMethod = XposedHelpers2.findMethodExactIfExists(
                currentClass,
                "getView",
                java.lang.Integer.TYPE,
                View::class.java,
                ViewGroup::class.java
            )
            if (getViewMethod != null) break
            currentClass = currentClass.superclass
        }

        if (getViewMethod == null) return

        val getViewMethodIDText = getViewMethod.toString()
        if (MainHook.uniqueMetaStore.contains(getViewMethodIDText)) return
        
        XposedHelpers2.hookMethod(
            getViewMethod,
            object : XC_MethodHook2() {

                override fun beforeHookedMethod(param: MethodHookParam) {
                    val adapter = param.thisObject as ListAdapter
                    val position = (param.args[0] as? Int?) ?: return
                    val itemData = adapter.getItem(position) ?: return
                    val chatUser = XposedHelpers2.getObjectField<Any>(itemData, "field_username") as? String ?: return

                    if (WXMaskPlugin.containChatUser(chatUser)) {
                        val option = ConfigUtil.getOptionData()
                        if (option.enableMapConversation) {
                            val maskBean = WXMaskPlugin.getMaskBeamById(chatUser)
                            if (maskBean != null) {
                                // 伪装头像加载器：渲染前瞬移 ID
                                param.setObjectExtra("real_wxid", chatUser)
                                val targetId = if (maskBean.mapId.isNullOrBlank()) getAutoTarget(chatUser).first else maskBean.mapId
                                XposedHelpers2.setObjectField(itemData, "field_username", targetId)
                            }
                        }
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val adapter: ListAdapter = param.thisObject as ListAdapter
                    val position: Int = (param.args[0] as? Int?) ?: return
                    val itemData: Any = adapter.getItem(position) ?: return
                    val itemView: View = param.args[1] as? View ?: return

                    val realWxid = param.getObjectExtra("real_wxid") as? String

                    // 【核心复用恢复逻辑】防止开启隐藏模式后，正常的好友也被连带隐藏
                    val lp = itemView.layoutParams
                    if (lp != null && lp.height == 1) {
                        itemView.visibility = View.VISIBLE
                        lp.height = -2 // WRAP_CONTENT
                        itemView.layoutParams = lp
                        itemView.setPadding(0, 0, 0, 0)
                        itemView.setOnClickListener(null) // 清除残留的拦截器
                    }

                    if (realWxid != null) {
                        // 立刻把底层 ID 还回去，维持原生功能的稳定性（如长按删除）
                        XposedHelpers2.setObjectField(itemData, "field_username", realWxid)

                        // 【新增功能：彻底隐藏模式】
                        if (completelyHideMainUI) {
                            itemView.visibility = View.GONE
                            val hideParams = itemView.layoutParams ?: AbsListView.LayoutParams(-1, 1)
                            hideParams.height = 1 // 设置为 1 像素，防止 ListView 崩溃
                            itemView.layoutParams = hideParams
                            itemView.setPadding(0, 0, 0, 0)
                            itemView.setOnClickListener(null)
                            return // 直接退出，不需要再渲染名字和消息了
                        }

                        val maskBean = WXMaskPlugin.getMaskBeamById(realWxid)
                        
                        // 【冷启动完美覆盖】将 UI 更新任务丢到主线程队列最末尾，确保覆盖微信自己的异步赋值！
                        itemView.post {
                            if (maskBean != null) {
                                val nameTvIdName = when (AppVersionUtil.getVersionCode()) {
                                    Constrant.WX_CODE_8_0_69 -> "kbq" 
                                    else -> "kbq" 
                                }
                                val nameViewId = ResUtil.getViewId(nameTvIdName)
                                if (nameViewId != 0 && nameViewId != View.NO_ID) {
                                    try {
                                        val nameTv: View? = itemView.findViewById(nameViewId)
                                        if (nameTv != null) {
                                            val customName = if (maskBean.tagName.isNullOrBlank() && maskBean.mapId.isNullOrBlank()) {
                                                getAutoTarget(realWxid).second
                                            } else if (!maskBean.tagName.isNullOrBlank()) {
                                                maskBean.tagName
                                            } else {
                                                "文件传输助手"
                                            }
                                            XposedHelpers2.callMethod<Any?>(nameTv, "setText", customName)
                                        }
                                    } catch (e: Throwable) {
                                        LogUtil.w("修改名字失败", e)
                                    }
                                }
                            }
                            hideUnReadTipView(itemView)
                            hideMsgViewItemText(itemView)
                        }

                        // 【完美解决乱跳】覆写原生的单次点击事件，强行跳转到盲盒号，完全不影响长按删除原聊天！
                        itemView.setOnClickListener {
                            try {
                                val targetId = if (maskBean?.mapId.isNullOrBlank()) getAutoTarget(realWxid).first else maskBean!!.mapId
                                val intent = Intent()
                                intent.setClassName(itemView.context, "com.tencent.mm.ui.chatting.ChattingUI")
                                intent.putExtra("Chat_User", targetId)
                                // 加上强开 Flag，确保必定跳出新的 ChattingUI
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                itemView.context.startActivity(intent)
                            } catch (e: Throwable) {
                                LogUtil.w("点击拦截跳转失败", e)
                            }
                        }
                    } else {
                        val chatUser = XposedHelpers2.getObjectField<Any>(itemData, "field_username") as? String ?: return
                        if (WXMaskPlugin.containChatUser(chatUser)) {
                            // 兜底逻辑
                            itemView.post {
                                hideUnReadTipView(itemView)
                                hideMsgViewItemText(itemView)
                            }
                        }
                    }
                }
            })
        MainHook.uniqueMetaStore.add(getViewMethodIDText)
    }

    private fun findGetItemMethod(adapterClazz: Class<*>?): Method? {
        if (adapterClazz == null) return null
        var method: Method? = XposedHelpers2.findMethodExactIfExists(adapterClazz, GetItemMethodName, Integer.TYPE)
        if (method != null) return method
        
        var methods = XposedHelpers2.findMethodsByExactPredicate(adapterClazz) { m ->
            val ret = !arrayOf(
                Object::class.java, String::class.java, Byte::class.java, Short::class.java,
                Long::class.java, Float::class.java, Double::class.java, java.lang.Byte.TYPE,
                java.lang.Short.TYPE, java.lang.Integer.TYPE, java.lang.Long.TYPE,
                java.lang.Float.TYPE, java.lang.Double.TYPE, java.lang.Void.TYPE
            ).contains(m.returnType)
            val paramVail = m.parameterTypes.size == 1 && m.parameterTypes[0] == Integer.TYPE
            return@findMethodsByExactPredicate paramVail && ret && Modifier.isPublic(m.modifiers) && !Modifier.isAbstract(m.modifiers)
        }
        if (methods.size > 0) {
            method = methods[0]
        }
        return method
    }

    // ========== 封装的 UI 隐藏辅助方法 ==========
    private fun hideUnReadTipView(itemView: View) {
        val tipTvIdTextID = when (AppVersionUtil.getVersionCode()) {
            in 0..Constrant.WX_CODE_8_0_22 -> "tipcnt_tv"
            Constrant.WX_CODE_PLAY_8_0_42 -> "oqu"
            in Constrant.WX_CODE_8_0_22..Constrant.WX_CODE_8_0_41 -> "kmv"
            else -> "kmv"
        }
        val tipTvId = ResUtil.getViewId(tipTvIdTextID)
        itemView.findViewById<View>(tipTvId)?.visibility = View.INVISIBLE

        val small_red = when (AppVersionUtil.getVersionCode()) {
            in 0..Constrant.WX_CODE_8_0_40 -> "a2f"
            Constrant.WX_CODE_PLAY_8_0_42 -> "a_w"
            Constrant.WX_CODE_8_0_41 -> "o_u"
            else -> "o_u"
        }
        val viewId = ResUtil.getViewId(small_red)
        itemView.findViewById<View>(viewId)?.visibility = View.INVISIBLE
    }

    private fun hideMsgViewItemText(itemView: View) {
        val msgTvIdName = when (AppVersionUtil.getVersionCode()) {
            in 0..Constrant.WX_CODE_8_0_22 -> "last_msg_tv"
            in Constrant.WX_CODE_8_0_22..Constrant.WX_CODE_8_0_40 -> "fhs"
            Constrant.WX_CODE_PLAY_8_0_42 -> "i2_"
            Constrant.WX_CODE_8_0_41 -> "ht5"
            else -> "ht5" 
        }
        val lastMsgViewId = ResUtil.getViewId(msgTvIdName)
        if (lastMsgViewId != 0 && lastMsgViewId != View.NO_ID) {
            try {
                val msgTv: View? = itemView.findViewById(lastMsgViewId)
                XposedHelpers2.callMethod<Any?>(msgTv, "setText", "")
            } catch (e: Throwable) {}
        } else {
            val ClazzNoMeasuredTextView = ClazzN.from("com.tencent.mm.ui.base.NoMeasuredTextView")
            ChildDeepCheck().each(itemView) { child ->
                try {
                    if (ClazzNoMeasuredTextView?.isAssignableFrom(child::class.java) == true
                        || TextView::class.java.isAssignableFrom(child::class.java)
                    ) {
                        XposedHelpers2.callMethod<String?>(child, "setText", "")
                    }
                } catch (e: Throwable) {}
            }
        }
    }
}
