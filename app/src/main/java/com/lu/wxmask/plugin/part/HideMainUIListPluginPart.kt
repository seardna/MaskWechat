package com.lu.wxmask.plugin.part
import android.content.Context
import android.graphics.Bitmap.Config
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import com.lu.lposed.api2.XC_MethodHook2
import com.lu.lposed.api2.XposedHelpers2
import com.lu.lposed.plugin.IPlugin
import com.lu.magic.util.GsonUtil
import com.lu.magic.util.ResUtil
import com.lu.magic.util.log.LogUtil
import com.lu.magic.util.view.ChildDeepCheck
import com.lu.wxmask.ClazzN
import com.lu.wxmask.Constrant
import com.lu.wxmask.MainHook
import com.lu.wxmask.plugin.WXConfigPlugin
import com.lu.wxmask.plugin.WXMaskPlugin
import com.lu.wxmask.plugin.ui.MaskUtil
import com.lu.wxmask.util.AppVersionUtil
import com.lu.wxmask.util.ConfigUtil
import com.lu.wxmask.util.ext.getViewId
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
/**
 * 主页UI（即微信底部“微信”Tab选中时所在页面）处理，消息、小红点相关逻辑
 */
class HideMainUIListPluginPart : IPlugin {
    val GetItemMethodName = when (AppVersionUtil.getVersionCode()) {
        Constrant.WX_CODE_8_0_22 -> "aCW"
        in Constrant.WX_CODE_8_0_22..Constrant.WX_CODE_8_0_43 -> "k"
        Constrant.WX_CODE_PLAY_8_0_48 -> "l"
        Constrant.WX_CODE_8_0_49, Constrant.WX_CODE_8_0_51,  Constrant.WX_CODE_8_0_56 -> "l"
        Constrant.WX_CODE_8_0_50 -> "n"
        Constrant.WX_CODE_8_0_53 -> "m"
        Constrant.WX_CODE_PLAY_8_0_69 -> "a" // 👉 添加 8.0.69
        else -> "m"
    }


    
        
          
    

        
        Expand All
    
    @@ -49,7 +50,6 @@ class HideMainUIListPluginPart : IPlugin {
  
    override fun handleHook(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            handleMainUIChattingListView2(context, lpparam)
        }.onFailure {
            LogUtil.w("hide mainUI listview fail, try to old function.")
            handleMainUIChattingListView(context, lpparam)
        }
    }

    //隐藏指定用户的主页的消息

    
        
          
    

        
        Expand All
    
    @@ -65,9 +65,10 @@ class HideMainUIListPluginPart : IPlugin {
  
    private fun handleMainUIChattingListView(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        val adapterName = when (AppVersionUtil.getVersionCode()) {
            Constrant.WX_CODE_8_0_22 -> "com.tencent.mm.ui.conversation.k"
            in Constrant.WX_CODE_8_0_32..Constrant.WX_CODE_8_0_34 -> {
                if (AppVersionUtil.getVersionName() == "8.0.35") {
                    "com.tencent.mm.ui.conversation.r"
                } else {
                    "com.tencent.mm.ui.conversation.p"
                }
            }

            Constrant.WX_CODE_8_0_35 -> "com.tencent.mm.ui.conversation.r"
            in Constrant.WX_CODE_8_0_35..Constrant.WX_CODE_8_0_41 -> "com.tencent.mm.ui.conversation.x"
            Constrant.WX_CODE_8_0_47 -> "com.tencent.mm.ui.conversation.p3"
            Constrant.WX_CODE_8_0_50 -> "com.tencent.mm.ui.conversation.q3"
            Constrant.WX_CODE_PLAY_8_0_69 -> "com.tencent.mm.ui.conversation.a" // 👉 添加 8.0.69
            else -> null
        }
        var adapterClazz: Class<*>? = null

    
          
            
    

          
          Expand Down
          
            
    

          
          Expand Up
    
    @@ -108,6 +109,54 @@ class HideMainUIListPluginPart : IPlugin {
  
        if (adapterName != null) {
            adapterClazz = ClazzN.from(adapterName, context.classLoader)
        }
        if (adapterClazz != null) {
            LogUtil.d("WeChat MainUI main Tap List Adapter", adapterClazz)
            hookListViewAdapter(adapterClazz)
        } else {
            LogUtil.w("WeChat MainUI not found Adapter for ListView, guess start.")
            val setAdapterMethod = XposedHelpers2.findMethodExactIfExists(
                ListView::class.java.name,
                context.classLoader,
                "setAdapter",
                ListAdapter::class.java
            )
            if (setAdapterMethod == null) {
                LogUtil.w("setAdapterMethod is null")
                return
            }
            XposedHelpers2.hookMethod(
                setAdapterMethod,
                object : XC_MethodHook2() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val adapter = param.args[0] ?: return
                        LogUtil.i("hook List adapter ", adapter)
                        if (adapter::class.java.name.startsWith("com.tencent.mm.ui.conversation")) {
                            LogUtil.w(AppVersionUtil.getSmartVersionName(), "guess adapter: ", adapter)
                            hookListViewAdapter(adapter.javaClass)
                        }
                    }
                }
            )
        }
    }

    private fun hookListViewAdapter(adapterClazz: Class<*>) {
        // 8.0.69 是 RecyclerView，使用 onBindViewHolder
        if (AppVersionUtil.getVersionCode() == Constrant.WX_CODE_PLAY_8_0_69) {
            XposedHelpers2.hookMethod(
                adapterClazz,
                "onBindViewHolder",
                RecyclerView.ViewHolder::class.java,
                Int::class.java,
                object : XC_MethodHook2() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val holder = param.args[0] as RecyclerView.ViewHolder
                        val position = param.args[1] as Int
                        val itemData = XposedHelpers2.callMethod(param.thisObject, GetItemMethodName, position) ?: return
                        val itemView = holder.itemView

                        val chatUser: String = XposedHelpers2.getObjectField(itemData, "field_username") ?: return
                        if (WXMaskPlugin.containChatUser(chatUser)) {
                            hideUnReadTipView(itemView, param)
                            hideMsgViewItemText(itemView, param)
                        }
                    }

                    private fun hideUnReadTipView(itemView: View, param: MethodHookParam) {
                        val tipTvId = ResUtil.getViewId("kmv")
                        itemView.findViewById<View>(tipTvId)?.visibility = View.INVISIBLE
                        val smallRedId = ResUtil.getViewId("o_u")
                        itemView.findViewById<View>(smallRedId)?.visibility = View.INVISIBLE
                    }

                    private fun hideMsgViewItemText(itemView: View, param: MethodHookParam) {
                        val lastMsgViewId = ResUtil.getViewId("ht5")
                        if (lastMsgViewId != 0) {
                            itemView.findViewById<TextView>(lastMsgViewId)?.text = ""
                        } else {
                            val targetClazz = ClazzN.from("com.tencent.mm.ui.base.NoMeasuredTextView")
                            ChildDeepCheck().each(itemView) {
                                if (targetClazz?.isInstance(it) == true || it is TextView) {
                                    (it as? TextView)?.text = ""
                                }
                            }
                        }
                    }
                }
            )
            MainHook.uniqueMetaStore.add(adapterClazz.name + "_onBindViewHolder")
            return
        }

        // 旧版本 ListView getView
        val getViewMethod: Method = XposedHelpers2.findMethodExactIfExists(
            adapterClazz,
            "getView",

    
        
          
    

        
        Expand All
    
    @@ -134,8 +183,6 @@ class HideMainUIListPluginPart : IPlugin {
  
            java.lang.Integer.TYPE,
            View::class.java,
            ViewGroup::class.java
        ) ?: return
        val getViewMethodIDText = getViewMethod.toString()
        if (MainHook.uniqueMetaStore.contains(getViewMethodIDText)) {
            return
        }
        LogUtil.w(getViewMethod)
        val baseConversationClazz = ClazzN.from(ClazzN.BaseConversation)
        XposedHelpers2.hookMethod(
            getViewMethod,
            object : XC_MethodHook2() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val adapter: ListAdapter = param.thisObject as ListAdapter
                    val position: Int = (param.args[0] as? Int?) ?: return
                    val itemData: Any = adapter.getItem(position) ?: return
                    LogUtil.d("after getView", adapter.javaClass, GsonUtil.toJson(itemData))
                    if (baseConversationClazz?.isAssignableFrom(itemData.javaClass) != true
                        && !itemData::class.java.name.startsWith("com.tencent.mm.storage")
                    ) {
                        LogUtil.w(
                            AppVersionUtil.getSmartVersionName(),
                            "类型检查错误，尝试继续",

    
        
          
    

        
        Expand All
    
    @@ -148,21 +195,15 @@ class HideMainUIListPluginPart : IPlugin {
  
                            itemData::class.java,
                            itemData::class.java.classes
                        )
                    }
                    val chatUser: String = XposedHelpers2.getObjectField(itemData, "field_username") ?: return
                    val itemView: View = param.args[1] as? View ?: return
                    if (WXMaskPlugin.containChatUser(chatUser)) {
                        hideUnReadTipView(itemView, param)
                        hideMsgViewItemText(itemView, param)
                    }
                }

                private fun hideLastMsgTime(itemView: View, params: MethodHookParam) {
                    val viewId = ResUtil.getViewId("l0s")
                    itemView.findViewById<View>(viewId)?.visibility = View.INVISIBLE
                }

                private fun hideUnReadTipView(itemView: View, param: MethodHookParam) {
                    val tipTvIdTextID = when (AppVersionUtil.getVersionCode()) {
                        in 0..Constrant.WX_CODE_8_0_22 -> "tipcnt_tv"
                        Constrant.WX_CODE_PLAY_8_0_42 -> "oqu"

    
        
          
    

        
        Expand All
    
    @@ -172,7 +213,6 @@ class HideMainUIListPluginPart : IPlugin {
  
                        in Constrant.WX_CODE_8_0_22..Constrant.WX_CODE_8_0_41 -> "kmv"
                        else -> "kmv"
                    }
                    val tipTvId = ResUtil.getViewId(tipTvIdTextID)
                    itemView.findViewById<View>(tipTvId)?.visibility = View.INVISIBLE

                    val small_red = when (AppVersionUtil.getVersionCode()) {
                        in 0..Constrant.WX_CODE_8_0_40 -> "a2f"
                        Constrant.WX_CODE_PLAY_8_0_42 -> "a_w"

    
        
          
    

        
        Expand All
    
    @@ -183,9 +223,7 @@ class HideMainUIListPluginPart : IPlugin {
  
                        Constrant.WX_CODE_8_0_41 -> "o_u"
                        else -> "o_u"
                    }
                    val viewId = ResUtil.getViewId(small_red)
                    itemView.findViewById<View>(viewId)?.visibility = View.INVISIBLE
                }

                private fun hideMsgViewItemText(itemView: View, param: MethodHookParam) {
                    val msgTvIdName = when (AppVersionUtil.getVersionCode()) {
                        in 0..Constrant.WX_CODE_8_0_22 -> "last_msg_tv"
                        in Constrant.WX_CODE_8_0_22..Constrant.WX_CODE_8_0_40 -> "fhs"

    
        
          
    

        
        Expand All
    
    @@ -203,7 +241,6 @@ class HideMainUIListPluginPart : IPlugin {
  
                        Constrant.WX_CODE_PLAY_8_0_42 -> "i2_"
                        Constrant.WX_CODE_8_0_41 -> "ht5"
                        else -> "ht5"
                    }
                    val lastMsgViewId = ResUtil.getViewId(msgTvIdName)
                    LogUtil.d("mask last msg textView", lastMsgViewId)
                    if (lastMsgViewId != 0 && lastMsgViewId != View.NO_ID) {
                        try {
                            val msgTv: View? = itemView.findViewById(lastMsgViewId)
                            XposedHelpers2.callMethod<Any?>(msgTv, "setText", "")
                        } catch (e: Throwable) {
                            LogUtil.w("error", e)
                        }
                    } else {
                        LogUtil.w("主页last消息id版本不适配，开启暴力隐藏", AppVersionUtil.getSmartVersionName())
                        val ClazzNoMeasuredTextView = ClazzN.from("com.tencent.mm.ui.base.NoMeasuredTextView")
                        ChildDeepCheck().each(itemView) { child ->

    
        
          
    

        
        Expand All
    
    @@ -217,9 +254,7 @@ class HideMainUIListPluginPart : IPlugin {
  
                            try {
                                if (ClazzNoMeasuredTextView?.isAssignableFrom(child::class.java) == true
                                    || TextView::class.java.isAssignableFrom(child::class.java)
                                ) {
                                    XposedHelpers2.callMethod<String?>(child, "setText", "")
                                }
                            } catch (e: Throwable) {
                            }
                        }
                    }
                }
            })
        MainHook.uniqueMetaStore.add(getViewMethodIDText)
    }

    
          
            
    

          
          Expand Down
          
            
    

          
          Expand Up
    
    @@ -265,19 +300,16 @@ class HideMainUIListPluginPart : IPlugin {
  
    private fun findGetItemMethod(adapterClazz: Class<*>?): Method? {
        if (adapterClazz == null) {
            return null
        }
        var method: Method? = XposedHelpers2.findMethodExactIfExists(adapterClazz, GetItemMethodName, Integer.TYPE)
        if (method != null) {
            return method
        }
        var methods = XposedHelpers2.findMethodsByExactPredicate(adapterClazz) { m ->
            val ret = !arrayOf(
                Object::class.java,
                String::class.java,
                Byte::class.java,
                Short::class.java,
                Long::class.java,
                Float::class.java,
                Double::class.java,
                String::class.java,
                java.lang.Byte.TYPE,
                java.lang.Short.TYPE,
                java.lang.Integer.TYPE,
                java.lang.Long.TYPE,
                java.lang.Float.TYPE,
                java.lang.Double.TYPE,
                java.lang.Void.TYPE
            ).contains(m.returnType)
            val paramVail = m.parameterTypes.size == 1 && m.parameterTypes[0] == Integer.TYPE
            return@findMethodsByExactPredicate paramVail && ret && Modifier.isPublic(m.modifiers) && !Modifier.isAbstract(m.modifiers)
        }
        if (methods.size > 0) {
            method = methods[0]
            if (methods.size > 1) {
                LogUtil.d("find getItem methods: []--> " + methods.joinToString("\n"))
            }
            LogUtil.d("guess getItem method $method")
        }
        return method
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
            Constrant.WX_CODE_8_0_58 -> "com.tencent.mm.ui.k3"
            Constrant.WX_CODE_PLAY_8_0_69 -> "com.tencent.mm.ui.conversation.a" // 👉 添加 8.0.69
            else -> null
        }
        var getItemMethod = if (adapterClazzName != null) {

    
        
          
    

        
        Expand All
    
    @@ -290,7 +322,6 @@ class HideMainUIListPluginPart : IPlugin {
  
            findGetItemMethod(ClazzN.from(adapterClazzName))
        } else {
            null
        }
        if (getItemMethod != null) {
            hookListViewGetItem(getItemMethod)
            return
        }

        LogUtil.w("WeChat MainUI ListView not found adapter, guess start.")
        XposedHelpers2.findAndHookMethod(
            ListView::class.java,

    
        
          
    

        
        Expand All
    
    @@ -308,7 +339,6 @@ class HideMainUIListPluginPart : IPlugin {
  
            "setAdapter",
            ListAdapter::class.java,
            object : XC_MethodHook2() {
                private var isHookGetItemMethod = false
                override fun afterHookedMethod(param: MethodHookParam) {
                    val adapter = param.args[0] ?: return
                    LogUtil.d("List adapter ", adapter)
                    if (adapter::class.java.name.startsWith("com.tencent.mm.ui.conversation")) {
                        if (isHookGetItemMethod) {
                            return
                        }
                        LogUtil.w(AppVersionUtil.getSmartVersionName(), "guess setAdapter: ", adapter, adapter.javaClass.superclass)
                        var getItemMethod = findGetItemMethod(adapter::class.java.superclass)
                        if (getItemMethod == null) {
                            getItemMethod = XposedHelpers2.findMethodExactIfExists(adapter::class.java.superclass, "getItem", Integer.TYPE)
                        }

    
        
          
    

        
        Expand All
    
    @@ -322,7 +352,6 @@ class HideMainUIListPluginPart : IPlugin {
  
                        if (getItemMethod != null) {
                            hookListViewGetItem(getItemMethod)
                            isHookGetItemMethod = true
                        } else {
                            LogUtil.w("guess getItem method is ", getItemMethod)
                        }
                    }
                }
            }
        )
    }

    private fun hookListViewGetItem(getItemMethod: Method) {

    
        
          
    

        
        Expand All
    
    @@ -339,21 +368,17 @@ class HideMainUIListPluginPart : IPlugin {
  
        LogUtil.d(">>>>>>>>>>.", getItemMethod)
        XposedHelpers2.hookMethod(
            getItemMethod,
            object : XC_MethodHook2() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val itemData: Any = param.result ?: return
                    val chatUser: String? = XposedHelpers2.getObjectField(itemData, "field_username")
                    if (chatUser == null) {
                        LogUtil.w("chat user is null")
                        return
                    }
                    if (WXMaskPlugin.containChatUser(chatUser)) {
                        val option = ConfigUtil.getOptionData()
                        if (option.enableMapConversation) {
                            var maskBean = WXMaskPlugin.getMaskBeamById(chatUser)?.let {
                                XposedHelpers2.setObjectField(itemData, "field_username", it.mapId)
                            }
                        }
                        XposedHelpers2.setObjectField(itemData, "field_content", "")
                        XposedHelpers2.setObjectField(itemData, "field_digest", "")
                        XposedHelpers2.setObjectField(itemData, "field_unReadCount", 0)
                        XposedHelpers2.setObjectField(itemData, "field_UnReadInvite", 0)
                        XposedHelpers2.setObjectField(itemData, "field_unReadMuteCount", 0)
                        XposedHelpers2.setObjectField(itemData, "field_msgType", "1")

                        if (option.enableTravelTime && option.travelTime != 0L) {

    
        
          
    

        
        Expand All
    
    @@ -362,25 +387,9 @@ class HideMainUIListPluginPart : IPlugin {
  
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
}
