/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package com.phonemetra.turbo.messenger.query;

import com.phonemetra.turbo.messenger.AndroidUtilities;
import com.phonemetra.turbo.messenger.MessageObject;
import com.phonemetra.turbo.messenger.MessagesController;
import com.phonemetra.turbo.messenger.MessagesStorage;
import com.phonemetra.turbo.messenger.NotificationCenter;
import com.phonemetra.turbo.tgnet.ConnectionsManager;
import com.phonemetra.turbo.tgnet.RequestDelegate;
import com.phonemetra.turbo.tgnet.TLObject;
import com.phonemetra.turbo.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

@SuppressWarnings("unchecked")
public class MessagesSearchQuery {

    private static int reqId;
    private static int mergeReqId;
    private static long lastMergeDialogId;
    private static int lastReqId;
    private static int messagesSearchCount[] = new int[] {0, 0};
    private static boolean messagesSearchEndReached[] = new boolean[] {false, false};
    private static ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private static HashMap<Integer, MessageObject> searchResultMessagesMap[] = new HashMap[] {new HashMap<>(), new HashMap<>()};
    private static String lastSearchQuery;
    private static int lastReturnedNum;

    private static int getMask() {
        int mask = 0;
        if (lastReturnedNum < searchResultMessages.size() - 1 || !messagesSearchEndReached[0] || !messagesSearchEndReached[1]) {
            mask |= 1;
        }
        if (lastReturnedNum > 0) {
            mask |= 2;
        }
        return mask;
    }

    public static boolean isMessageFound(final int messageId, boolean mergeDialog) {
        return searchResultMessagesMap[mergeDialog ? 1 : 0].containsKey(messageId);
    }

    public static void searchMessagesInChat(String query, final long dialog_id, final long mergeDialogId, final int guid, final int direction, TLRPC.User user) {
        searchMessagesInChat(query, dialog_id, mergeDialogId, guid, direction, false, user);
    }

    private static void searchMessagesInChat(String query, final long dialog_id, final long mergeDialogId, final int guid, final int direction, final boolean internal, final TLRPC.User user) {
        int max_id = 0;
        long queryWithDialog = dialog_id;
        boolean firstQuery = !internal;
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
            reqId = 0;
        }
        if (mergeReqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(mergeReqId, true);
            mergeReqId = 0;
        }
        if (query == null) {
            if (searchResultMessages.isEmpty()) {
                return;
            }
            if (direction == 1) {
                lastReturnedNum++;
                if (lastReturnedNum < searchResultMessages.size()) {
                    MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                    return;
                } else {
                    if (messagesSearchEndReached[0] && mergeDialogId == 0 && messagesSearchEndReached[1]) {
                        lastReturnedNum--;
                        return;
                    }
                    firstQuery = false;
                    query = lastSearchQuery;
                    MessageObject messageObject = searchResultMessages.get(searchResultMessages.size() - 1);
                    if (messageObject.getDialogId() == dialog_id && !messagesSearchEndReached[0]) {
                        max_id = messageObject.getId();
                        queryWithDialog = dialog_id;
                    } else {
                        if (messageObject.getDialogId() == mergeDialogId) {
                            max_id = messageObject.getId();
                        }
                        queryWithDialog = mergeDialogId;
                        messagesSearchEndReached[1] = false;
                    }
                }
            } else if (direction == 2) {
                lastReturnedNum--;
                if (lastReturnedNum < 0) {
                    lastReturnedNum = 0;
                    return;
                }
                if (lastReturnedNum >= searchResultMessages.size()) {
                    lastReturnedNum = searchResultMessages.size() - 1;
                }
                MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                return;
            } else {
                return;
            }
        } else if (firstQuery) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsLoading, guid);
            messagesSearchEndReached[0] = messagesSearchEndReached[1] = false;
            messagesSearchCount[0] = messagesSearchCount[1] = 0;
            searchResultMessages.clear();
            searchResultMessagesMap[0].clear();
            searchResultMessagesMap[1].clear();
        }
        if (messagesSearchEndReached[0] && !messagesSearchEndReached[1] && mergeDialogId != 0) {
            queryWithDialog = mergeDialogId;
        }
        if (queryWithDialog == dialog_id && firstQuery) {
            if (mergeDialogId != 0) {
                TLRPC.InputPeer inputPeer = MessagesController.getInputPeer((int) mergeDialogId);
                if (inputPeer == null) {
                    return;
                }
                final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.peer = inputPeer;
                lastMergeDialogId = mergeDialogId;
                req.limit = 1;
                req.q = query != null ? query : "";
                if (user != null) {
                    req.from_id = MessagesController.getInputUser(user);
                    req.flags |= 1;
                }
                req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                mergeReqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (lastMergeDialogId == mergeDialogId) {
                                    mergeReqId = 0;
                                    if (response != null) {
                                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                        messagesSearchEndReached[1] = res.messages.isEmpty();
                                        messagesSearchCount[1] = res instanceof TLRPC.TL_messages_messagesSlice ? res.count : res.messages.size();
                                        searchMessagesInChat(req.q, dialog_id, mergeDialogId, guid, direction, true, user);
                                    }
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
                return;
            } else {
                lastMergeDialogId = 0;
                messagesSearchEndReached[1] = true;
                messagesSearchCount[1] = 0;
            }
        }
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInputPeer((int) queryWithDialog);
        if (req.peer == null) {
            return;
        }
        req.limit = 21;
        req.q = query != null ? query : "";
        req.offset_id = max_id;
        if (user != null) {
            req.from_id = MessagesController.getInputUser(user);
            req.flags |= 1;
        }
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        final int currentReqId = ++lastReqId;
        lastSearchQuery = query;
        final long queryWithDialogFinal = queryWithDialog;
        reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            reqId = 0;
                            if (response != null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                for (int a = 0; a < res.messages.size(); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    if (message instanceof TLRPC.TL_messageEmpty || message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                                        res.messages.remove(a);
                                        a--;
                                    }
                                }
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                if (req.offset_id == 0 && queryWithDialogFinal == dialog_id) {
                                    lastReturnedNum = 0;
                                    searchResultMessages.clear();
                                    searchResultMessagesMap[0].clear();
                                    searchResultMessagesMap[1].clear();
                                    messagesSearchCount[0] = 0;
                                }
                                boolean added = false;
                                for (int a = 0; a < Math.min(res.messages.size(), 20); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    added = true;
                                    MessageObject messageObject = new MessageObject(message, null, false);
                                    searchResultMessages.add(messageObject);
                                    searchResultMessagesMap[queryWithDialogFinal == dialog_id ? 0 : 1].put(messageObject.getId(), messageObject);
                                }
                                messagesSearchEndReached[queryWithDialogFinal == dialog_id ? 0 : 1] = res.messages.size() != 21;
                                messagesSearchCount[queryWithDialogFinal == dialog_id ? 0 : 1] = res instanceof TLRPC.TL_messages_messagesSlice || res instanceof TLRPC.TL_messages_channelMessages ? res.count : res.messages.size();
                                if (searchResultMessages.isEmpty()) {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, 0, getMask(), (long) 0, 0, 0);
                                } else {
                                    if (added) {
                                        if (lastReturnedNum >= searchResultMessages.size()) {
                                            lastReturnedNum = searchResultMessages.size() - 1;
                                        }
                                        MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                                    }
                                }
                                if (queryWithDialogFinal == dialog_id && messagesSearchEndReached[0] && mergeDialogId != 0 && !messagesSearchEndReached[1]) {
                                    searchMessagesInChat(lastSearchQuery, dialog_id, mergeDialogId, guid, 0, true, user);
                                }
                            }
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public static String getLastSearchQuery() {
        return lastSearchQuery;
    }
}
