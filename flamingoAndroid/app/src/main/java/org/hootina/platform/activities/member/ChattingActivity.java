package org.hootina.platform.activities.member;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import tms.User.FileLoadInfo;
import tms.User.FileUpInfo;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;

import org.hootina.platform.R;
import org.hootina.platform.activities.BaseActivity;
import org.hootina.platform.activities.MainActivity;
import org.hootina.platform.adapters.ChattingAdapter;
import org.hootina.platform.enums.ClientType;
import org.hootina.platform.enums.TabbarEnum;
import org.hootina.platform.enums.MsgType;
import org.hootina.platform.net.ChatMsgMgr;
import org.hootina.platform.net.ChatSessionMgr;
import org.hootina.platform.net.NetWorker;
import org.hootina.platform.result.ChatMessage;
import org.hootina.platform.result.ChatSession;
import org.hootina.platform.result.ContentText;
import org.hootina.platform.result.FileInfo;
import org.hootina.platform.result.FriendInfo;
import org.hootina.platform.result.FromMessageEntity;
import org.hootina.platform.result.MessageTextEntity;
import org.hootina.platform.result.SendMessage;
import org.hootina.platform.userinfo.UserInfo;
import org.hootina.platform.userinfo.UserSession;
import org.hootina.platform.utils.MyMD5;
import org.hootina.platform.utils.PictureUtil;
import org.hootina.platform.utils.ToastUtils;
import org.hootina.platform.utils.UIUtils;
import org.hootina.platform.widgets.x.XListView;
import org.hootina.platform.widgets.x.XListView.IXListViewListener;
import org.hootina.platform.util.MegAsnType;


/*
 * @聊天页面
 */
public class ChattingActivity extends BaseActivity implements IXListViewListener {
	private static final String FILE_BASE_DIR = "/sdcard/flamingo/";
	private static final String PHOTO_FILE_NAME = System.currentTimeMillis() + "jpg";
	private ImageView 			iv_emoticons_normal;
	private ImageView 			iv_emoticons_checked;
	private ImageView 			iv_sendPicture;
	private Button 				btn_more;
	private Button 				btn_send;
	private Button 				send_button;
	private EditText 			mSendTextEditor;
	private XListView 			chatting_lv;
	private TextView 			name;
	private RelativeLayout 		edittext_layout;
	private LinearLayout 		view_camera;
	private LinearLayout 		view_photo;

	private String 				type;
	private String 				editortalkmsg;
	private String 				talkmsg;
	private String 				text;
	private String 				msgtexts;
	private static String 		picname;

	private int 				mFriendID;
	private int 				mSenderID;
	private String              mFriendNickName;
	private int 				uMsgID;
	private int 				faceid;
	private int 				uOffset;

	private ChattingAdapter 	chatingAdapter;
	private FromMessageEntity 	fromMessageEntity;
	private MessageTextEntity 	messageTextEntity;
	private ContentText 		contentText;


	//private ChatSession 		chatSession;
	private List<ChatSession> 	infos;
	
	private List<ChatMessage> 	mPendingChatMessages = new ArrayList<ChatMessage>();
	private List<ChatMessage> 	mPendingMsgList = new ArrayList<ChatMessage>();
	private List<MessageTextEntity> mCurrentMessage = new ArrayList<>();

	private Timer 				mUpdateRecvMsgStateTimer;	//更新收到的聊天消息的状态的定时器
	private static final int	UPDATE_RECV_MSG_STATE_TIMER_ID	= 0xFF;

	private static final int 	PHOTO_REQUEST_CAMERA  = 1;
	private static final int 	PHOTO_REQUEST_GALLERY = 2;
	private static final int 	PHOTO_REQUEST_CUT     = 3;

	private File 				file;
	private File 				tempFile;
	private Bitmap 				bitmap;
	// private String path;
	//
	private byte[] 				strName;
	private byte[] 				contentIntleng;

	private List<FileInfo> 		m_downloadingFiles;
	private Map<Integer, FriendInfo> mapGroupMember = new HashMap<Integer, FriendInfo>();

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	@Override
	public void onClick(View v) {
		ClipboardManager cmb = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
		cmb.getText();

		switch (v.getId()) {
		case R.id.btn_back:
            navigateToMainActivity();
			break;

		case R.id.btn_send:
            UIUtils.hideSoftInput(this, mSendTextEditor);
			sendChatMsg();
            mSendTextEditor.setText("");
			break;


		case R.id.text_editor:
			break;

		case R.id.view_photo:
			// 相册获取
			gallery();
			break;

		case R.id.view_camera:
			// 照相
			camera();
			break;

		case R.id.iv_emoticons_normal:
			// 隐藏软键盘
			InputMethodManager imms = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imms.hideSoftInputFromWindow(mSendTextEditor.getWindowToken(), 0);
			break;

		default:
			break;
		}

	}

	@Override
	protected int getContentView() {
		return R.layout.activity_chatting;

	}

	@Override
	protected void initData() {
		mSenderID = UserSession.getInstance().loginUser.get_userid();
		if (mFriendID == 0)
            mFriendID = getIntent().getIntExtra("userid", 0);

        UserInfo u = UserSession.getInstance().getUserInfoById(mFriendID);
        if (u != null)
            mFriendNickName = u.get_nickname();

		name.setText(mFriendNickName);
		// 点击事件
        mSendTextEditor = (EditText)findViewById(R.id.text_editor);
		mSendTextEditor.setOnFocusChangeListener(new OnFocusChangeListener() {

			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					edittext_layout
							.setBackgroundResource(R.drawable.input_bar_bg_active);
				} else {
					edittext_layout
							.setBackgroundResource(R.drawable.input_bar_bg_normal);
				}

			}
		});
		mSendTextEditor.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				edittext_layout
						.setBackgroundResource(R.drawable.input_bar_bg_active);
				iv_emoticons_normal.setVisibility(View.VISIBLE);
				iv_emoticons_checked.setVisibility(View.INVISIBLE);
			}
		});
		// 检测内容事件
		mSendTextEditor.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (!TextUtils.isEmpty(s)) {
					//
					// startActivity(LoginActivity.class);
					String ss = s.toString();
					if (ss.startsWith("([") && ss.endsWith("])")) {
						String pics = ss.substring(2, ss.length() - 2);
						String pic = pics.replace("\"", "");
						String[] ssa = pic.split("\\,");
						String name = ssa[0];
						String uFilesize = ssa[2];
						// makeTextShort(a);
						mSendTextEditor.setText("");
						String strPath = "/sdcard/flamingo/" + name;
						bitmap = PictureUtil.decodePicFromFullPath(strPath);
						final File file = saveMyBitmap(bitmap, name);
						String strChecksum = MyMD5.getMD5(file);
						// String newFileName = uFilesize + strChecksum +
						// ".jpg";
						String newFileName = name;
						//contwo.sendFileUpInfo(newFileName, strChecksum, 0,
						//		Long.parseLong(uFilesize));
						sendpictrue(newFileName, strChecksum, 0,
								Long.parseLong(uFilesize));
					}
					btn_more.setVisibility(View.GONE);
					btn_send.setVisibility(View.VISIBLE);
				} else {
					btn_more.setVisibility(View.VISIBLE);
					btn_send.setVisibility(View.GONE);
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {

			}

		});

		mUpdateRecvMsgStateTimer = new Timer();
		mUpdateRecvMsgStateTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				// 定义一个消息传过去
				Message msg = new Message();
				msg.what = ChattingActivity.UPDATE_RECV_MSG_STATE_TIMER_ID;
				sendMessage(msg);
			}

		}, (long) 1000, (long) 5000);

		// 获取群数据
		int nGroupID = mFriendID;
		if (isGroup(nGroupID)) {
//			try {
////				List<FriendInfo> list = BaseActivity.getDb().findAll(
////						Selector.from(FriendInfo.class).where("mSelfID",
////								"=", nGroupID));
//				List<FriendInfo> list = null;
//
//				if (list != null) {
//
//					for (int i = 0; i < list.size(); ++i) {
//						FriendInfo info = list.get(i);
//						mapGroupMember.put(info.getuTargetID(), info);
//					}
//				}
//			} catch (DbException e) {
//
//				e.printStackTrace();
//			}
		}

		//AppData.setRead(mSelfID, nGroupID);

		//加载历史消息
		if (mCurrentMessage.isEmpty())
            mCurrentMessage = BaseActivity.getChatMsgDb().getChatMsgBySenderAndTargetID(mSenderID, mFriendID);
	}

	@Override
	protected void setData() {
		iv_sendPicture = (ImageView) findViewById(R.id.iv_sendPicture);
		chatting_lv.setXListViewListener(this);
		chatting_lv.setPullRefreshEnable(true);
		chatting_lv.setPullLoadEnable(false);
		chatting_lv.setAutoLoadEnable(false);

		RelativeLayout.LayoutParams statusTextViewParams = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT,
				RelativeLayout.LayoutParams.MATCH_PARENT);

		chatting_lv.setLayoutParams(statusTextViewParams);
		iv_emoticons_checked.setOnClickListener(this);
		view_photo = (LinearLayout) findViewById(R.id.view_photo);
		view_photo.setOnClickListener(this);
		view_camera = (LinearLayout) findViewById(R.id.view_camera);
		view_camera.setOnClickListener(this);
		// 转发
		type = getIntent().getStringExtra("type");
		msgtexts = getIntent().getStringExtra("msgtexts");
		if (!msgtexts.equals("")) {
//			if (type.equals("")) {
//				SendMessage sendMessage = new SendMessage();
//				sendMessage.setmClientType(3);
//				sendMessage.setFrom(mSenderID);
//				sendMessage.setTo(mFriendID);
//				sendMessage.setmMsgID(-1);
//				sendMessage.setmMsgType(1);
//				long epoch = System.currentTimeMillis() / 1000;
//				sendMessage.setmMsgTime(epoch);
//				List<Object> obj = new ArrayList<Object>();
//				msgtexts = msgtexts.replace("||", "|");
//				String[] ss = msgtexts.split("\\|");
//				for (int i = 0; i < ss.length; i++) {
//					Map<String, Object> map = new HashMap<String, Object>();
//					String id = ss[i];
//					if (id.startsWith("[") && id.endsWith("]")) {
//						String faceId = id.substring(1, id.length() - 1);
//						map.put("faceID", Integer.valueOf(faceId));
//
//					} else {
//
//						map.put("msgText", id);
//					}
//
//					obj.add(map);
//
//				}
//				sendMessage.setmContent(obj);
//				talkmsg = JSON.toJSONString(sendMessage);
//
//				try {
//					messages = new ChatMessage();
//					messages.setSenderID(mSenderID);
//					messages.setmMsgType(1);
//					// uMsgID = tTalkMsgAns.getUMsgID() + 1;
//					messages.setmMsgSenderClientType(1);
//					messages.setTargetID(mFriendID);
//
//					messages.setmMsgTime(epoch);
//					messages.setmMsgState(0);
//
//					List<ContentText> cons = new ArrayList<ContentText>();
//					ContentText ct = new ContentText();
//					ct.setMsgText(msgtexts);
//					cons.add(ct);
//					messages.setmContent(cons);
//					BaseActivity.getDb().save(messages);
//				} catch (DbException e) {
//					e.printStackTrace();
//				}
//				con.sendChatMsg(mSenderID, mFriendID, uMsgID, talkmsg);
//
//			}else{
//
//				if (msgtexts.startsWith("([") && msgtexts.endsWith("])")) {
//					String pics = msgtexts.substring(2, msgtexts.length() - 2);
//					String pic = pics.replace("\"", "");
//					String[] ssa = pic.split("\\,");
//					String name = ssa[0];
//					String uFilesize = ssa[2];
//
//					String strPath = FILE_BASE_DIR + name;
//					bitmap = PictureUtil.decodePicFromFullPath(strPath);
//					final File file = saveMyBitmap(bitmap, name);
//					String strChecksum = MyMD5.getMD5(file);
//					// String newFileName = uFilesize + strChecksum +
//					// ".jpg";
//					String newFileName = name;
//					contwo.sendFileUpInfo(newFileName, strChecksum, 0,
//							Long.parseLong(uFilesize));
//					sendpictrue(newFileName, strChecksum, 0,
//							Long.parseLong(uFilesize));
//				}
//			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		NetWorker.clearSenderCount();
		NetWorker.clearNotificationCount();

		refreshMessageList();
        updateUnreadChatMsgCount();
	}

	public void sendChatMsg(){
		//TODO: SendMessage类型最好和ChatMessage类型合并
//		SendMessage sendMessage = new SendMessage();
//		sendMessage.setmSenderClientType(ClientType.CLIENT_TYPE_ANDROID);
//		sendMessage.setmSenderID(mSenderID);
//		sendMessage.setmTargetID(mFriendID);
//		String msgID = UUID.randomUUID().toString();
//		sendMessage.setmMsgID(msgID);
//		sendMessage.setmMsgType(ChatMessage.CONTENT_TYPE_TEXT);
//		long msgTime = System.currentTimeMillis() / 1000;
//		sendMessage.setmMsgTime(msgTime);
//		List<Object> obj = new ArrayList<Object>();
//		String rawChatMsg1 = mSendTextEditor.getText().toString();


		String rawChatMsg1 = mSendTextEditor.getText().toString().trim();
		if (rawChatMsg1.isEmpty()) {
            ToastUtils.showLongToast(this, "发送消息不能为空");
            return;
        }

        MessageTextEntity entity = MessageTextEntity.rawStringToEntity(rawChatMsg1);
        entity.setClientType(ClientType.CLIENT_TYPE_ANDROID);
        entity.setSenderID(mSenderID);
        entity.setTargetID(mFriendID);
        String msgID = MessageTextEntity.generateMsgID();
        entity.setMsgID(msgID);
        entity.setMsgType(MessageTextEntity.CONTENT_TYPE_TEXT);
        long msgTime = System.currentTimeMillis() / 1000;
        entity.setMsgTime(msgTime);
        mCurrentMessage.add(entity);


        //chatMsg格式：
        //{"clientType":2,"content":[{"faceID":7},{"faceID":7},{"faceID":-1,"msgText":"gg"}],
        // "msgID":"9c07a6a5-93fa-4b83-bcf3-6059a1d354b2","msgTime":1522746339,"msgType":1,"senderID":1,"targetID":2}
		String chatMsg = JSON.toJSONString(entity);
		ChatMsgMgr.getInstance().addMessageTextEntity(mFriendID, entity);


        //chatContentJson格式：
        //[{"faceID":7},{"faceID":7},{"faceID":-1,"msgText":"gg"}]
		String chatContentJson = entity.contentToJson();
		//数据库中存储的是原始的消息文字
		BaseActivity.getChatMsgDb().insertChatMsg(msgID, msgTime, mSenderID,  mFriendID,
                                                  MessageTextEntity.CONTENT_TYPE_TEXT, chatContentJson,
                                                  ClientType.CLIENT_TYPE_ANDROID, 0,
                                                 "");



		refreshMessageList();
		//在这里将聊天消息发出去
		NetWorker.sendChatMsg(mFriendID, chatMsg);


        ChatSessionMgr.getInstance().updateSession(mFriendID, mFriendNickName, chatContentJson, "");

		//更新session列表
//		ChatSession chatSession = AppData.updateChatSessionByFriendID(mFriendID);
//		chatSession.setFriendNickName(mFriendNickName);
//		//显示最后一条消息
//		chatSession.setLastMsg(rawChatMsg1);
//		chatSession.setFriendID(mFriendID);
//		chatSession.setmSelfID(mSelfID);
//		//在聊天窗口显示消息，当前session的可读消息为0
//		chatSession.setUnreadCount(0);
	}

	public void loadfile(ChatMessage msg) {

		String strText = msg.getmMsgText();
		String strTemp = strText.substring(2, strText.length() - 2);
		String[] strArray = strTemp.split("\\,");
		String strLocalName = strArray[0];
		strLocalName = strLocalName.replaceAll("\"", "");
		String strPics = strArray[1];
		String strPic = strPics.replaceAll("\"", "");
		String path = FILE_BASE_DIR + strPics;
		file = new File(path);
		if (file.exists()) {

		} else {
			PictureUtil.loadfile(strLocalName, strPic);
		}
	}

	public int getTargetID() {
		return mFriendID;
	}

	public String getFriendName(int nID) {
		if (mapGroupMember == null) {
			return null;
		}
		FriendInfo info = mapGroupMember.get(nID);
		if (info == null) {
			return null;
		}
		return info.getStrNickName();
	}

	private void loadSendingMsg()
	{
//		try {
//			mPendingChatMessages = BaseActivity.getDb()
//					.findAll(
//							Selector.from(ChatMessage.class)
//									.where("mMsgState", "=", ChatMessage.MSG_STATE_SENDING)
//									.and(WhereBuilder.b("mSenderID", "=", mSelfID)
//											.b("mTargetID", "=", mFriendID)));
//
//		}
//		catch (DbException e) {
//			e.printStackTrace();
//		}
	}

	private int updateUnreadRecvMsgToReadState() {
//		List<ChatMessage> pendingChatMessages = null;
//		int nUpdateCount = 0;
//		try {
//
//			pendingChatMessages = BaseActivity.getDb().findAll(
//								Selector.from(ChatMessage.class)
//										.where("mTargetID", "=", mSelfID)
//										.and(WhereBuilder.b("mSenderID", "=", mFriendID)));
//
//			if (pendingChatMessages == null || pendingChatMessages.isEmpty())
//				return nUpdateCount;
//
//			for(int i = 0; i < pendingChatMessages.size(); ++i)
//			{
//				ChatMessage msg = pendingChatMessages.get(i);
//				if(msg == null)
//				{
//					pendingChatMessages.remove(i);
//					i--;
//					continue;
//				}
//
//				//只处理未读消息
//				if(msg.getMsgState() != ChatMessage.MSG_STATE_UNREAD)
//				{
//					pendingChatMessages.remove(i);
//					i--;
//					continue;
//				}
//			}
//
//			//没有未读消息，直接返回
//			if (pendingChatMessages.isEmpty())
//				return nUpdateCount;
//
//			ChatMessage chatMessage;
//			for (int i = 0; i < pendingChatMessages.size(); ++i) {
//				chatMessage = pendingChatMessages.get(i);
//				chatMessage.setMsgState(ChatMessage.MSG_STATE_READ);
//				BaseActivity.getDb().update(chatMessage);
//
//				++nUpdateCount;
//			}
//
//		} catch (DbException e) {
//			e.printStackTrace();
//		}
//
//		return nUpdateCount;
		return 0;
	}

	public void setMessageFailed() {
//		if (mSelfID != 0 && BaseActivity.getDb() != null) {
//			try {
//
//				List<ChatMessage> tis = BaseActivity.getDb().findAll(
//						Selector.from(ChatMessage.class));
//
//				if (tis != null && tis.size() > 0) {
//					int nRefreshCount = 0;
//					for (int i = 0; i < tis.size(); ++i) {
//						ChatMessage msg = tis.get(i);
//						if (msg.getMsgState() == 0) {
//							msg.setMsgState(2);
//							BaseActivity.getDb().update(msg);
//							nRefreshCount++;
//						}
//					}
//
//					if (nRefreshCount > 0) {
//						refreshMessageList();
//					}
//				}
//			} catch (DbException e) {
//				e.printStackTrace();
//			}
//		}
	}

	private void refreshMessageList() {
		if (mCurrentMessage.isEmpty())
			return;

		chatingAdapter = new ChattingAdapter(this, mCurrentMessage);
		chatting_lv.setAdapter(chatingAdapter);
		chatting_lv.setSelection(chatingAdapter.getCount() - 1);
        //chatting_lv.notifyD
//
//		msgListSize = tidyPendingMsgList.size();
//		for (int i = 0; i < msgListSize; i++) {
//			msgItem = tidyPendingMsgList.get(i);
//			String idd = msgItem.getmMsgText();
//			if (msgItem.getMsgType() == ChatMessage.CONTENT_TYPE_IMAGE_CONFIRM ||
//					msgItem.getMsgType() == ChatMessage.CONTENT_TYPE_MOBILE_IMAGE) {
//
//				//非自己发送的图
//				if (msgItem.getSenderID() != mSelfID) {
//					String a = idd.substring(2, idd.length() - 2);
//					String[] sss = a.split("\\,");
//					String name = sss[0];
//					name = name.replaceAll("\"", "");
//					String img = FILE_BASE_DIR + name;
//
//					File file = new File(img);
//
//					if (!file.exists()) {
//						loadfile(msgItem);
//					}
//				}
//			}
//		}
	}

	private void updateUnreadChatMsgCount(){
	    ChatMsgMgr.getInstance().clearUnreadChatMsgCountBySenderID(mFriendID);
    }

	@Override
	public void processMessage(Message msg) {
		//super.processMessage(msg);

		if (msg.what == ChattingActivity.UPDATE_RECV_MSG_STATE_TIMER_ID) {
			int updateMsgCount = updateUnreadRecvMsgToReadState();
			//if (updateMsgCount > 0)
				//refreshMessageList();

		} else if (msg.what == MsgType.msg_type_chat) {
			//更新session
			//MessageTextEntity messageTextEntity = JSONObject.parseObject((String)msg.obj, MessageTextEntity.class);
			int senderID = msg.arg1;
			int targetID = msg.arg2;

			//TODO： 消息越多越慢，改从内存中取消息
			mCurrentMessage = BaseActivity.getChatMsgDb().getChatMsgBySenderAndTargetID(senderID, targetID);

			refreshMessageList();



//			ChatSession chatSession = AppData.updateChatSessionByFriendID(senderID);
//			chatSession.setmNickName(mFriendNickName);
//			//显示最后一条消息
//			chatSession.setmLastMsgText(messageTextEntity.getmContent().get(messageTextEntity.getmContent().size() - 1).getMsgText());
//			chatSession.setmFriendID(mFriendID);
//			chatSession.setmSelfID(mSelfID);
//			//在聊天窗口显示消息，当前session的可读消息为0
//			chatSession.setNotRead(0);
//
//			//TODO: 万一找不到就挂了
//            String senderNickName = UserSession.getInstance().getUserInfoById(senderID).get_nickname();
//            String targetNickName = UserSession.getInstance().getUserInfoById(senderID).get_nickname();
//
//            String msgID = UUID.randomUUID().toString();
//            int msgType = messageTextEntity.getmMsgType();
//            String chatMsg = "";
//            List<ContentText> contentText = messageTextEntity.getmContent();
//            for (int i = 0; i < contentText.size(); ++i){
//                if (msgType == ChatMessage.CONTENT_TYPE_TEXT)
//                    chatMsg += contentText.get(i).getMsgText();
//            }
//
//            BaseActivity.getChatMsgDb().insertChatMsg(msgID, ChatMessage.CONTENT_TYPE_TEXT,
//                    senderID, senderNickName, targetID,
//                    targetNickName, chatMsg, 0, "");


		}/* else if (msg.what == MsgType.msg_type_chat) {
			MessageTextEntity messageTextEntity = JSONObject.parseObject((String)msg.obj, MessageTextEntity.class);
			int senderId = msg.arg1;
			int targetId = msg.arg2;


			ChatMessage messages = new ChatMessage();
			messages.setSenderID(senderId);// haoyou
			messages.setmMsgID(-1);
			messages.setTargetID(targetId);
			messages.setmMsgType(messageTextEntity.getmMsgType());
			messages.setmMsgID(String.valueOf(-1));
			messages.setSenderID(senderId);
			messages.setmMsgSenderClientType(messageTextEntity.getmMsgSenderClientType());
			messages.setmMsgTime(messageTextEntity.getmMsgTime());
			messages.setmContent(messageTextEntity.getmContent());

			if (messages.getmMsgType() == 2 || messages.getmMsgType() == 5) {
				loadfile(messages);
			}

			refreshMessageList();

			AppData.setRead(mSelfID, mFriendID);

		} */else if (msg.what == MegAsnType.Topicturemsg) {
			FileLoadInfo fileLoad = (FileLoadInfo) msg.obj;
			String name = fileLoad.getStrName().toStringUtf8();
			// String path = FILE_BASE_DIR + name;
			// File file = new File(path);

			int uError = 0;
			int uOffset = fileLoad.getUOffset();
			int uDownsize = fileLoad.getUDownsize();
			PictureUtil.topictrue(name, uError, uOffset, uDownsize);

		} else if (msg.what == MegAsnType.FileLoadData) {
			refreshMessageList();

		} else if (msg.what == MegAsnType.FileUpData) {
			// 上传图片成功
			FileUpInfo fileUpInfo = (FileUpInfo) msg.obj;
			String name = fileUpInfo.getStrName().toStringUtf8();
			int uFilesize = fileUpInfo.getUFilesize();

			// 更新消息状态
			updateUnreadRecvMsgToReadState();

			int TargetID = mFriendID;
			SendMessage sendMessage = new SendMessage();
			//sendMessage.setmClientType(3);
			//sendMessage.setFrom(mSenderID);
			//sendMessage.setTo(TargetID);
			//sendMessage.setmMsgID(uMsgID);
			//sendMessage.setmMsgType(5);
			long epoch = System.currentTimeMillis() / 1000;
			//sendMessage.setmMsgTime(epoch);
			List<Object> obj = new ArrayList<Object>();
			Map<String, Object> map = new HashMap<String, Object>();

			int nWidth = 0;
			int nHeight = 0;
			File file = new File("/flamingo/" + name);
			if (file.exists()) {
				BitmapFactory.Options opts = new BitmapFactory.Options();
				opts.inPreferredConfig = Bitmap.Config.RGB_565;
				opts.inJustDecodeBounds = true;
				BitmapFactory.decodeFile(FILE_BASE_DIR + name, opts);
				nWidth = opts.outWidth;
				nHeight = opts.outHeight;
			}

			Object[] strArray = { name, fileUpInfo.getStrUrl(), uFilesize,
					nWidth, nHeight };
			map.put("pic", strArray);
			obj.add(map);
			sendMessage.setContent(obj);
			talkmsg = JSON.toJSONString(sendMessage);
			// con.sendmsg(mSenderID, TargetID, uMsgID, talkmsg);
			int uMsgID = -1;
			//con.sendChatMsg(mSenderID, TargetID, uMsgID, talkmsg);
//			chatSession.setStrNickName(mFriendNickName);
//			chatSession.setTextid(talkmsg);
			int nTargetID = mFriendID;
//			chatSession.setmFriendID(nTargetID);
//			chatSession.setmSelfID(mSenderID);
//			try {
////				infos = BaseActivity.getDb().findAll(
////						Selector.from(ChatSession.class).where(
////								"mSelfID", "=",
////								application.getMemberEntity().getuAccountID()));
////				if (infos != null) {
////					for (int i = 0; i < infos.size(); i++) {
////						if (infos.get(i).getmFriendID() == nTargetID) {
////							db.delete(infos.get(i));
////							break;
////						}
////					}
////				}
//				//BaseActivity.getDb().save(chatSession);
//				//AppData.updateChatSessionByFriendID(chatSession);
//			} catch (DbException e) {
//				e.printStackTrace();
//			}
		} else if (msg.what == MegAsnType.Refresh) {
			refreshMessageList();
		}

	}

	/*
	 * 照相
	 */
	public void camera() {
		Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
		// 判断是否有SD卡
		if (hasSdcard()) {
			intent.putExtra(MediaStore.EXTRA_OUTPUT,
					Uri.fromFile(new File(Environment
							.getExternalStorageDirectory(), PHOTO_FILE_NAME)));
		}
		startActivityForResult(intent, PHOTO_REQUEST_CAMERA);
	}

	/*
	 * 从相册获取
	 */
	public void gallery() {
		// 判断存储卡是否可以用，可用进行存储
		Intent intent = new Intent(Intent.ACTION_PICK);
		intent.setType("image/*");
		startActivityForResult(intent, PHOTO_REQUEST_GALLERY);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PHOTO_REQUEST_GALLERY) {
			if (data != null) {
				// 得到图片的全路径
				Uri uri = data.getData();

				String strPath = null;

				if (data.getScheme().toString().compareTo("content") == 0) {
					String[] proj = { MediaStore.Images.Media.DATA };
					Cursor cursor = getContentResolver().query(uri, proj, null,
							null, null);
					if (cursor.moveToFirst()) {
						;
						int column_index = cursor
								.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
						strPath = cursor.getString(column_index);
					}
				} else {
					strPath = uri.getPath();
				}

				long uFilesize = 0;
				File ff = new File(strPath);
				if (ff.exists()) {
				} else {
					return;
				}

				uFilesize = ff.length();

				bitmap = PictureUtil.decodePicFromFullPath(strPath);
				if (bitmap == null) {
					Log.i("open image failed : ", strPath);
					return;
				} else {
					String name = mSenderID + System.currentTimeMillis() + ".jpg";
					final File file = saveMyBitmap(bitmap, name);

					// 压缩没有效果
					if (ff.length() <= file.length()) {
						file.delete();

						try {
							java.io.FileInputStream fosfrom = new java.io.FileInputStream(
									ff);
							java.io.FileOutputStream fosto = new FileOutputStream(
									file);
							try {
								byte[] bt = new byte[1024 * 100];
								int c;
								while ((c = fosfrom.read(bt)) > 0) {
									fosto.write(bt, 0, c); // 灏嗗唴瀹瑰啓鍒版柊鏂囦欢褰撲腑
								}

								fosfrom.close();
								fosto.close();
							} catch (Exception ex) {

								Log.e("readfile", ex.getMessage());

							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

					String strChecksum = MyMD5.getMD5(file);
					int uDownsize = 0;
					uFilesize = file.length();

					String newFileName = uFilesize + strChecksum + ".jpg";
					File newFile = new File(FILE_BASE_DIR + newFileName);
					if (newFile.exists()
							&& !newFile.getName().equals(file.getName())) {
						newFile.delete();
					}
					file.renameTo(newFile);

					//contwo.sendFileUpInfo(newFileName, strChecksum, 0, uFilesize);
					sendpictrue(newFileName, strChecksum, 0, uFilesize);
				}
				if (data != null) {

				}
			}

		} else if (requestCode == PHOTO_REQUEST_CAMERA) {
			if (hasSdcard()) {
				tempFile = new File(Environment.getExternalStorageDirectory(),
						PHOTO_FILE_NAME);

				String strPath = tempFile.getPath();

				PictureUtil.deleteFromMemCache(strPath);
				bitmap = PictureUtil.decodePicFromFullPath(strPath);
				if (bitmap == null) {
					return;
				}
				String name = mSenderID + System.currentTimeMillis() + ".jpg";
				final File file = saveMyBitmap(bitmap, name);

				String strChecksum = MyMD5.getMD5(file);
				int uDownsize = 0;
				long uFilesize = file.length();

				String newFileName = uFilesize + strChecksum + ".jpg";
				File newFile = new File(FILE_BASE_DIR + newFileName);
				if (newFile.exists()) {
					newFile.delete();
				}
				file.renameTo(newFile);
				// 涓婁紶鍥剧墖
				//contwo.sendFileUpInfo(newFileName, strChecksum, uDownsize, uFilesize);
				sendpictrue(newFileName, strChecksum, uDownsize, uFilesize);

			} else {
				Toast.makeText(ChattingActivity.this, "鏈壘鍒板瓨鍌ㄥ崱锛屾棤娉曞瓨鍌ㄧ収鐗囷紒",
						0).show();
			}

		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private boolean hasSdcard() {
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			return true;
		} else {
			return false;
		}
	}

	public File saveMyBitmap(Bitmap mBitmap, String bitName) {
		File f = new File(FILE_BASE_DIR + bitName);
		if (f.exists()) {
			f.delete();
		}
		FileOutputStream fOut = null;
		try {
			fOut = new FileOutputStream(f);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
		int options = 100;
		while (out.toByteArray().length / 1024 > 200 && options >= 50) {
			options -= 10;
			out.reset();
			mBitmap.compress(Bitmap.CompressFormat.JPEG, options, out);
		}

		try {
			fOut.write(out.toByteArray());
			fOut.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			fOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return f;
	}

	@Override
	public void onRefresh() {

	}

	@Override
	public void onLoadMore() {

	}

	// 相册或相机发送图片
	public void sendpictrue(String newFileName, String strChecksum,
			int uDownsize, long uFilesize) {

//		int TargetID = mFriendID;
//		SendMessage sendMessage = new SendMessage();
//		//sendMessage.setmClientType(3);
//		//sendMessage.setFrom(mSenderID);
//		//sendMessage.setTo(TargetID);
//		//sendMessage.setmMsgID(uMsgID);
//		//sendMessage.setmMsgType(5);
//		long epoch = System.currentTimeMillis() / 1000;
//		//sendMessage.setmMsgTime(epoch);
//		List<Object> obj = new ArrayList<Object>();
//		Map<String, Object> map = new HashMap<String, Object>();
//
//		Object[] strArray = { newFileName, strChecksum, uFilesize, 0, 0 };
//		map.put("pic", strArray);
//		obj.add(map);
//		sendMessage.setmContent(obj);
//		String talkmsgaa = JSON.toJSONString(sendMessage);
//		try {
//			ChatMessage messages = new ChatMessage();
//			messages.setSenderID(mSenderID);// haoyou
//			messages.setmMsgID("");
//			messages.setSenderID(mSenderID);
//			messages.setmMsgType(5);
//			messages.setTargetID(mFriendID);
//			messages.setMsgSenderClientType(3);
//			messages.setMsgTime(epoch);
//			List<ContentText> cons = new ArrayList<ContentText>();
//			ContentText ct = new ContentText();
//			String talkmsges = talkmsgaa.substring(
//					talkmsgaa.indexOf("[{\"pic\":["), talkmsgaa.indexOf("]}]"));
//			talkmsges = talkmsges.replace("[{\"pic\":[", "[");
//			String talkmsger = talkmsges + "]";
//			ct.setPic(talkmsger);
//			cons.add(ct);
//			messages.setmContent(cons);
//			//BaseActivity.getDb().save(messages);
//
//			loadSendingMsg();
//
//		} catch (DbException e) {
//			e.printStackTrace();
//		}
//		refreshMessageList();

	}

	public static byte[] getBytesFromFile(File f) {
		if (f == null) {
			return null;
		}
		try {
			FileInputStream stream = new FileInputStream(f);
			ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
			byte[] b = new byte[1000];
			int n;
			while ((n = stream.read(b)) != -1) {
				out.write(b, 0, n);
			}
			stream.close();
			out.close();
			return out.toByteArray();
		} catch (IOException e) {
		}
		return null;
	}

	@Override
	public void onBackPressed() {
		//super.onBackPressed();
        navigateToMainActivity();
	}

	private void navigateToMainActivity(){
        Intent i = new Intent(this, MainActivity.class);
        application.setTabIndex(TabbarEnum.MESSAGE);
        startActivity(i);
        finish();
    }
}
