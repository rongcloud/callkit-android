package io.rong.callkit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

import io.rong.callkit.util.CallKitSearchBarListener;
import io.rong.callkit.util.CallKitSearchBarView;
import io.rong.callkit.util.CallKitUtils;
import io.rong.callkit.util.CallSelectMemberSerializable;
import io.rong.calllib.RongCallCommon;
import io.rong.common.RLog;
import io.rong.imkit.feature.mention.RongMentionManager;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imkit.userinfo.model.GroupUserInfo;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Group;
import io.rong.imlib.model.UserInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CallSelectMemberActivity extends BaseNoActionBarActivity implements RongUserInfoManager.UserDataObserver {
    private static final String TAG = "CallSelectMemberActivity";
    public static final String DISCONNECT_ACTION = "call_disconnect";
    ArrayList<String> selectedMember;
    private boolean isFirstDialog = true;
    /** 已经选择的观察者列表 */
    private ArrayList<String> observerMember;

    TextView txtvStart, callkit_conference_selected_number;
    ListAdapter mAdapter;
    ListView mList;
    RongCallCommon.CallMediaType mMediaType;
    private Conversation.ConversationType conversationType;
    private EditText searchView;
    private HashMap<String, String> tempNickmembers = new HashMap<>();

    private ArrayList<UserInfo> searchMembers = new ArrayList<>();
    private ArrayList<String> invitedMembers;
    private ArrayList<UserInfo> tempMembers = new ArrayList<>();

    private ArrayList<String> allObserver = null; // 保存当前通话中从多人音/视频传递过来的观察者列表

    private String groupId;
    private RelativeLayout rlSearchTop;
    private RelativeLayout rlActionBar;
    private ImageView ivBack;
    private CallKitSearchBarView searchBar;
    /**
     * true:只能选择n个人同时进行音视频通话，>n选择无效; false:>n个人同时音视频通话之后,其他人视为观察者加入到本次通话中; n ：NORMAL_VIDEO_NUMBER 和
     * NORMAL_AUDIO_NUMBER
     */
    private boolean ctrlTag = true;

    private static final int NORMAL_VIDEO_NUMBER = 7;
    private static final int NORMAL_AUDIO_NUMBER = 20;
    private ArrayList<UserInfo> userInfoArrayList = new ArrayList<>();
    /** 用于存储获取不到userInfo的用户在列表中的位置 */
    private HashMap<String, Integer> userInfoIndex = new HashMap<>();

    private final Object listLock = new Object();
    //
    private Handler uiHandler =
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (msg.what == 2) {
                        Bundle bundle = msg.getData();
                        if (bundle != null) {
                            CallSelectMemberSerializable callSelectMemberSerializable =
                                    (CallSelectMemberSerializable)
                                            bundle.getSerializable(
                                                    CALLSELECTMEMBERSERIALIZABLE_KEY);
                            if (callSelectMemberSerializable != null) {
                                tempNickmembers = callSelectMemberSerializable.getHashMap();
                            }
                        }
                        if (userInfoArrayList.isEmpty()
                                && invitedMembers != null
                                && invitedMembers.size() > 0) {
                            String tmpUserID = "";
                            for (int i = 0; i < invitedMembers.size(); i++) {
                                tmpUserID = invitedMembers.get(i);
                                fillInUserInfoList(tmpUserID, i);
                            }
                        }
                        RLog.i(TAG, "setAdapter");
                        mAdapter = new ListAdapter(userInfoArrayList, invitedMembers);
                        mList.setAdapter(mAdapter);
                        callkit_conference_selected_number.setText(
                                getString(
                                        R.string.callkit_selected_contacts_count,
                                        getTotalSelectedNumber()));
                    }
                }
            };

    private void fillInUserInfoList(String userid, int index) {
        synchronized (listLock) {
            if (!TextUtils.isEmpty(userid)) {
                if (getUserInfo(userid) == null) {
                    userInfoIndex.put(userid, index);
                }
                // 当获取到的userInfo为空时，记录下当前index，并给userInfoArrayList增加一个空元素，等到异步结果(onHeadsetPlugUpdate)回来后，根据index，把正确的数据插入回原来位置
                userInfoArrayList.add(getUserInfo(userid));

            } else {
                RLog.e(TAG, "uiHandler->userid null.");
            }
        }
    }

    private Handler mHandler;
    private static final String GROUPMEMBERSRESULT_KEY = "GROUPMEMBERSRESULTKEY";
    private static final String CALLSELECTMEMBERSERIALIZABLE_KEY =
            "CALLSELECTMEMBERSERIALIZABLEKEY";

    private BroadcastReceiver disconnectBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), DISCONNECT_ACTION)) {
                if (!isFinishing()) {
                    setActivityResult(true);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow()
                .setFlags(
                        WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_call_select_member2);
        RongUserInfoManager.getInstance().addUserDataObserver(this);

        initTopBar();

        selectedMember = new ArrayList<>();
        observerMember = new ArrayList<>();

        Intent intent = getIntent();
        int type = intent.getIntExtra("mediaType", RongCallCommon.CallMediaType.VIDEO.getValue());
        mMediaType = RongCallCommon.CallMediaType.valueOf(type);
        int conType = intent.getIntExtra("conversationType", 0);
        conversationType = Conversation.ConversationType.setValue(conType);
        invitedMembers = intent.getStringArrayListExtra("invitedMembers");
        groupId = intent.getStringExtra("groupId");
        allObserver = intent.getStringArrayListExtra("allObserver");

        ArrayList<String> list = intent.getStringArrayListExtra("allMembers");
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                fillInUserInfoList(list.get(i), i);
            }
        }
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler =
                new Handler(handlerThread.getLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        String key = (String) msg.obj;
                        if (GROUPMEMBERSRESULT_KEY.equals(key)) {
                            Bundle bundle = msg.getData();
                            HashMap<String, String> hashMap = new HashMap<>();
                            if (bundle != null) {
                                ArrayList<UserInfo> arrayList =
                                        bundle.getParcelableArrayList(GROUPMEMBERSRESULT_KEY);
                                Conversation.ConversationType conversationType =
                                        Conversation.ConversationType.setValue(
                                                bundle.getInt("conversationType"));
                                if (arrayList != null) {
                                    RLog.i(TAG, "onGetGroupMembersResult : " + arrayList.size());
                                    UserInfo userInfo = null;
                                    String userNickName = "";
                                    GroupUserInfo groupUserInfo = null;
                                    /** 转换昵称** */
                                    for (int i = 0; i < arrayList.size(); i++) {
                                        userInfo = arrayList.get(i);
                                        if (userInfo != null
                                                && !TextUtils.isEmpty(userInfo.getUserId())) {
                                            if (conversationType != null
                                                    && conversationType.equals(
                                                            Conversation.ConversationType.GROUP)) {
                                                groupUserInfo =
                                                        RongUserInfoManager.getInstance()
                                                                .getGroupUserInfo(
                                                                        groupId,
                                                                        userInfo.getUserId());
                                                if (groupUserInfo != null
                                                        && !TextUtils.isEmpty(
                                                                groupUserInfo.getNickname())) {
                                                    userNickName = groupUserInfo.getNickname();
                                                }
                                            }
                                            if (TextUtils.isEmpty(userNickName)) {
                                                userNickName = userInfo.getName();
                                            } else {
                                                userInfo.setName(userNickName);
                                            }
                                            hashMap.put(userInfo.getUserId(), userNickName);
                                            userNickName = "";
                                        }
                                    }
                                }
                            }
                            CallSelectMemberSerializable callSelectMemberSerializable =
                                    new CallSelectMemberSerializable(hashMap);
                            Message message = new Message();
                            message.what = 2;
                            Bundle bundle1 = new Bundle();
                            bundle1.putSerializable(
                                    CALLSELECTMEMBERSERIALIZABLE_KEY, callSelectMemberSerializable);
                            message.setData(bundle1);
                            uiHandler.sendMessage(message);
                        }
                    }
                };

        RongCallKit.GroupMembersProvider provider = RongCallKit.getGroupMemberProvider();
        if (TextUtils.isEmpty(groupId)) {
            return;
        }
        if (provider != null) {
            provider.getMemberList(
                    groupId,
                    new RongCallKit.OnGroupMembersResult() {
                        @Override
                        public void onGotMemberList(ArrayList<String> members) {
                            for (int i = 0; i < members.size(); i++) {
                                fillInUserInfoList(members.get(i), i);
                            }
                            Message message = new Message();
                            message.obj = GROUPMEMBERSRESULT_KEY;
                            Bundle bundle = new Bundle();
                            bundle.putParcelableArrayList(
                                    GROUPMEMBERSRESULT_KEY, userInfoArrayList);
                            bundle.putInt("conversationType", conversationType.getValue());
                            message.setData(bundle);
                            mHandler.sendMessage(message);
                        }
                    });
        } else {
            if (RongMentionManager.getInstance().getGroupMembersProvider() != null) {
                RongMentionManager.getInstance()
                        .getGroupMembersProvider()
                        .getGroupMembers(
                                groupId,
                                new RongMentionManager.IGroupMemberCallback() {
                                    @Override
                                    public void onGetGroupMembersResult(List<UserInfo> userInfos) {
                                        if (userInfos == null || userInfos.size() == 0) {
                                            RLog.e(
                                                    TAG,
                                                    "onGetGroupMembersResult userInfos is null!");
                                            return;
                                        }
                                        userInfoArrayList.addAll(userInfos);

                                        Message message = new Message();
                                        message.obj = GROUPMEMBERSRESULT_KEY;
                                        Bundle bundle = new Bundle();
                                        bundle.putParcelableArrayList(
                                                GROUPMEMBERSRESULT_KEY, userInfoArrayList);
                                        bundle.putInt(
                                                "conversationType", conversationType.getValue());
                                        message.setData(bundle);
                                        mHandler.sendMessage(message);
                                    }
                                });
            }
        }

        callkit_conference_selected_number =
                (TextView) findViewById(R.id.callkit_conference_selected_number);
        txtvStart = (TextView) findViewById(R.id.callkit_btn_ok);
        txtvStart.setText(getString(R.string.callkit_voip_ok));
        txtvStart.setEnabled(false);
        txtvStart.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setActivityResult(false);
                    }
                });

        mList = (ListView) findViewById(R.id.calkit_list_view_select_member);
        mList.setOnItemClickListener(adapterOnItemClickListener);
        rlSearchTop = (RelativeLayout) findViewById(R.id.rl_search_top);
        ivBack = (ImageView) findViewById(R.id.iv_back);
        searchBar = (CallKitSearchBarView) findViewById(R.id.search_bar);
        ivBack.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rlSearchTop.setVisibility(View.GONE);
                        rlActionBar.setVisibility(View.VISIBLE);
                        mAdapter.setAllMembers(userInfoArrayList);
                        mAdapter.notifyDataSetChanged();
                        CallKitUtils.closeKeyBoard(CallSelectMemberActivity.this, null);
                    }
                });
        searchBar.setSearchBarListener(
                new CallKitSearchBarListener() {
                    @Override
                    public void onSearchStart(String content) {
                        if (userInfoArrayList != null && userInfoArrayList.size() > 0) {
                            startSearchMember(content);
                        } else {
                            Toast.makeText(
                                            CallSelectMemberActivity.this, getString(R.string.rc_voip_search_no_member),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }

                    @Override
                    public void onSoftSearchKeyClick() {}

                    @Override
                    public void onClearButtonClick() {
                        if (invitedMembers != null) {
                            mAdapter = new ListAdapter(userInfoArrayList, invitedMembers);
                            mList.setAdapter(mAdapter);
                            mList.setOnItemClickListener(adapterOnItemClickListener);
                        }
                    }
                });
        registerDisconnectBroadcastReceiver();
    }

    private void registerDisconnectBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DISCONNECT_ACTION);
        registerReceiver(disconnectBroadcastReceiver, intentFilter);
    }

    private void startSearchMember(String searchEditContent) {
        try {
            searchMembers.clear();
            tempMembers.clear();
            if (!TextUtils.isEmpty(searchEditContent)) {
                for (UserInfo info : userInfoArrayList) {
                    if (info != null && !TextUtils.isEmpty(info.getUserId())) {
                        if (((String) tempNickmembers.get(info.getUserId()))
                                        .indexOf(searchEditContent)
                                != -1) {
                            tempMembers.add(info);
                        }
                    }
                }
            } else {
                tempMembers.addAll(userInfoArrayList);
            }
        } catch (Exception e) {
            e.printStackTrace();
            tempMembers.addAll(userInfoArrayList);
        }
        //        closeKeyBoard(this, searchBar);
        setData();
    }

    private void setData() {
        if (null != tempMembers) {
            ListAdapter adapter = new ListAdapter(tempMembers, invitedMembers);
            mList.setAdapter(adapter);
            mList.setOnItemClickListener(adapterOnItemClickListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RongUserInfoManager.getInstance().removeUserDataObserver(this);
        unregisterReceiver(disconnectBroadcastReceiver);
    }

    @Override
    public void onUserUpdate(UserInfo userInfo) {
        if (mList != null && userInfo != null) {
            synchronized (listLock) {
                int index = userInfoIndex.get(userInfo.getUserId());
                if (index >= 0 && index < userInfoArrayList.size()) {
                    userInfoIndex.remove(userInfo.getUserId());
                    userInfoArrayList.remove(index);
                    userInfoArrayList.add(index, userInfo);
                }
                Message message = new Message();
                message.obj = GROUPMEMBERSRESULT_KEY;
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList(GROUPMEMBERSRESULT_KEY, userInfoArrayList);
                bundle.putInt("conversationType", conversationType.getValue());
                message.setData(bundle);
                mHandler.sendMessage(message);
            }
        }
    }

    @Override
    public void onGroupUpdate(Group group) {

    }

    @Override
    public void onGroupUserInfoUpdate(GroupUserInfo groupUserInfo) {

    }

    class ListAdapter extends BaseAdapter {
        List<UserInfo> mallMembers;
        List<String> invitedMembers;

        public ListAdapter(List<UserInfo> allMembers, List<String> invitedMembers) {
            this.mallMembers = allMembers;
            this.invitedMembers = invitedMembers;
        }

        public void setAllMembers(List<UserInfo> allMembers) {
            this.mallMembers = allMembers;
        }

        @Override
        public int getCount() {
            return mallMembers.size();
        }

        @Override
        public Object getItem(int position) {
            return mallMembers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView =
                        LayoutInflater.from(CallSelectMemberActivity.this)
                                .inflate(R.layout.rc_voip_listitem_select_member, null);
                holder.checkbox = (ImageView) convertView.findViewById(R.id.rc_checkbox);
                holder.portrait = (ImageView) convertView.findViewById(R.id.rc_user_portrait);
                holder.name = (TextView) convertView.findViewById(R.id.rc_user_name);
                convertView.setTag(holder);
            }

            UserInfo mUserInfo = mallMembers.get(position);
            if (mUserInfo == null || TextUtils.isEmpty(mUserInfo.getUserId())) {
                // userInfo为空前，把所有值都设置为默认
                holder = (ViewHolder) convertView.getTag();
                holder.checkbox.setImageResource(R.drawable.rc_voip_checkbox);
                holder.checkbox.setClickable(false);
                holder.checkbox.setEnabled(true);
                holder.name.setText("");
                Glide.with(holder.portrait)
                        .load(R.drawable.rc_default_portrait)
                        .apply(RequestOptions.bitmapTransform(new CenterCrop()))
                        .into(holder.portrait);
                holder.checkbox.setTag("");
                return convertView;
            }
            holder = (ViewHolder) convertView.getTag();
            holder.checkbox.setTag(mUserInfo.getUserId());
            if (invitedMembers.contains(mUserInfo.getUserId())) {
                holder.checkbox.setClickable(false);
                holder.checkbox.setEnabled(false);
                holder.checkbox.setImageResource(R.drawable.rc_voip_icon_checkbox_checked);
            } else {
                if (selectedMember.contains(mUserInfo.getUserId())) {
                    holder.checkbox.setImageResource(R.drawable.rc_voip_checkbox);
                    holder.checkbox.setSelected(true);
                } else {
                    holder.checkbox.setImageResource(R.drawable.rc_voip_checkbox);
                    holder.checkbox.setSelected(false);
                }
                holder.checkbox.setClickable(false);
                holder.checkbox.setEnabled(true);
            }

            String displayName = "";
            if (conversationType != null
                    && conversationType.equals(Conversation.ConversationType.GROUP)) {
                GroupUserInfo groupUserInfo =
                        RongUserInfoManager.getInstance()
                                .getGroupUserInfo(groupId, mUserInfo.getUserId());
                if (groupUserInfo != null && !TextUtils.isEmpty(groupUserInfo.getNickname())) {
                    displayName = groupUserInfo.getNickname();
                }
            }
            if (TextUtils.isEmpty(displayName)) {
                holder.name.setText(mUserInfo.getName());
            } else {
                holder.name.setText(displayName);
            }
            Glide.with(holder.portrait)
                    .load(mUserInfo.getPortraitUri())
                    .placeholder(R.drawable.rc_default_portrait)
                    .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                    .into(holder.portrait);
            return convertView;
        }
    }

    /**
     * 结束页面前设置值
     *
     * @param val 是否是远端挂断，如果是则关闭该页面
     */
    private void setActivityResult(boolean val) {
        Intent intent = new Intent();
        intent.putExtra("remote_hangup", val);
        intent.putStringArrayListExtra("invited", selectedMember);
        intent.putStringArrayListExtra("observers", observerMember);
        setResult(RESULT_OK, intent);
        CallSelectMemberActivity.this.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    class ViewHolder {

        ImageView checkbox;
        ImageView portrait;
        TextView name;
    }

    public void initTopBar() {
        rlActionBar = (RelativeLayout) findViewById(R.id.rl_actionbar);
        ImageButton backImgBtn = (ImageButton) findViewById(R.id.imgbtn_custom_nav_back);
        backImgBtn.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
        TextView titleTextView = (TextView) findViewById(R.id.tv_custom_nav_title);
        titleTextView.setText(getString(R.string.rc_select_contact));
        titleTextView.setTextSize(18);
        titleTextView.setTextColor(getResources().getColor(R.color.callkit_normal_text));

        findViewById(R.id.imgbtn_custom_nav_option).setVisibility(View.VISIBLE);
        ((ImageButton) findViewById(R.id.imgbtn_custom_nav_option))
                .setImageResource(R.drawable.callkit_ic_search_focused_x);
        findViewById(R.id.imgbtn_custom_nav_option)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                rlSearchTop.setVisibility(View.VISIBLE);
                                rlActionBar.setVisibility(View.GONE);
                            }
                        });
    }

    private AdapterView.OnItemClickListener adapterOnItemClickListener =
            new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    View v = view.findViewById(R.id.rc_checkbox);
                    String userId = (String) v.getTag();
                    if (!TextUtils.isEmpty(userId) && !invitedMembers.contains(userId)) {
                        if (v.isSelected()) {
                            if (selectedMember.contains(userId)) {
                                selectedMember.remove(userId);
                            }
                            if (observerMember.contains(userId)) {
                                observerMember.remove(userId);
                            }
                            v.setSelected(false);
                            if (selectedMember.size() == 0 && observerMember.size() == 0) {
                                txtvStart.setEnabled(false);
                                txtvStart.setTextColor(
                                        getResources()
                                                .getColor(
                                                        R.color
                                                                .callkit_color_text_operation_disable));
                                callkit_conference_selected_number.setTextColor(
                                        getResources()
                                                .getColor(
                                                        R.color
                                                                .callkit_color_text_operation_disable));
                            }
                            if (searchMembers != null) {
                                callkit_conference_selected_number.setText(
                                        getString(
                                                R.string.callkit_selected_contacts_count,
                                                getTotalSelectedNumber()));
                            }
                            return;
                        }
                        int totalNumber = getTotalSelectedNumber();
                        boolean videoObserverState =
                                totalNumber
                                        >= (mMediaType.equals(RongCallCommon.CallMediaType.AUDIO)
                                                ? NORMAL_AUDIO_NUMBER
                                                : NORMAL_VIDEO_NUMBER);
                        if (ctrlTag) {
                            if (videoObserverState) {
                                Toast.makeText(
                                                CallSelectMemberActivity.this,
                                                String.format(
                                                        getString(
                                                                mMediaType.equals(
                                                                                RongCallCommon
                                                                                        .CallMediaType
                                                                                        .AUDIO)
                                                                        ? R.string
                                                                                .rc_voip_audio_numberofobservers
                                                                        : R.string
                                                                                .rc_voip_video_numberofobservers),
                                                        totalNumber),
                                                Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }
                            if (selectedMember.contains(userId)) {
                                selectedMember.remove(userId);
                            }
                            v.setSelected(!v.isSelected()); // 1 false
                            if (v.isSelected()) {
                                selectedMember.add(userId);
                            }
                            if (selectedMember.size() > 0 || observerMember.size() > 0) {
                                txtvStart.setEnabled(true);
                                txtvStart.setTextColor(
                                        getResources().getColor(R.color.rc_voip_check_enable));
                                callkit_conference_selected_number.setTextColor(
                                        getResources().getColor(R.color.rc_voip_check_enable));
                            } else {
                                txtvStart.setEnabled(false);
                                txtvStart.setTextColor(
                                        getResources()
                                                .getColor(
                                                        R.color
                                                                .callkit_color_text_operation_disable));
                                callkit_conference_selected_number.setTextColor(
                                        getResources()
                                                .getColor(
                                                        R.color
                                                                .callkit_color_text_operation_disable));
                            }
                        } else {
                            if (videoObserverState && isFirstDialog) {
                                CallPromptDialog dialog =
                                        CallPromptDialog.newInstance(
                                                CallSelectMemberActivity.this,
                                                getString(R.string.rc_voip_video_observer));
                                dialog.setPromptButtonClickedListener(
                                        new CallPromptDialog.OnPromptButtonClickedListener() {
                                            @Override
                                            public void onPositiveButtonClicked() {}

                                            @Override
                                            public void onNegativeButtonClicked() {}
                                        });
                                dialog.disableCancel();
                                dialog.setCancelable(false);
                                dialog.show();
                                isFirstDialog = false;
                            }
                            v.setSelected(!v.isSelected()); // 1 false
                            if (videoObserverState) {
                                if (observerMember.contains(userId)) {
                                    observerMember.remove(userId);
                                }
                                observerMember.add(userId);
                            }
                            if (selectedMember.contains(userId)) {
                                selectedMember.remove(userId);
                            }
                            if (v.isSelected()) {
                                selectedMember.add(userId);
                            }
                            if (selectedMember.size() > 0 || observerMember.size() > 0) {
                                txtvStart.setEnabled(true);
                                txtvStart.setTextColor(
                                        getResources().getColor(R.color.rc_voip_check_enable));
                                callkit_conference_selected_number.setTextColor(
                                        getResources().getColor(R.color.rc_voip_check_enable));
                            } else {
                                txtvStart.setEnabled(false);
                                txtvStart.setTextColor(
                                        getResources()
                                                .getColor(
                                                        R.color
                                                                .callkit_color_text_operation_disable));
                                callkit_conference_selected_number.setTextColor(
                                        getResources()
                                                .getColor(
                                                        R.color
                                                                .callkit_color_text_operation_disable));
                            }
                        }
                    }
                    if (searchMembers != null) {
                        callkit_conference_selected_number.setText(
                                getString(
                                        R.string.callkit_selected_contacts_count,
                                        getTotalSelectedNumber()));
                    }
                }
            };

    /**
     * 关闭软键盘
     *
     * @param activity
     * @param view
     */
    private void closeKeyBoard(Activity activity, View view) {
        IBinder token;
        if (view == null || view.getWindowToken() == null) {
            if (null == activity) {
                return;
            }
            Window window = activity.getWindow();
            if (window == null) {
                return;
            }
            View v = window.peekDecorView();
            if (v == null) {
                return;
            }
            token = v.getWindowToken();
        } else {
            token = view.getWindowToken();
        }
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(token, 0);
    }

    private UserInfo getUserInfo(String userid) {
        if (TextUtils.isEmpty(userid)) {
            return null;
        }
        return RongUserInfoManager.getInstance().getUserInfo(userid);
    }

    private int getTotalSelectedNumber() {
        return (selectedMember == null ? 0 : selectedMember.size())
                + (invitedMembers == null ? 0 : invitedMembers.size());
    }
}
