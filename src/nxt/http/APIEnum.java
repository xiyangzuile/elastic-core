/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.http;

import java.util.Base64;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum APIEnum {
    // To preserve compatibility, please add new APIs to the end of the enum.
    // When an API is deleted, set its name to empty string and handler to null.
    BROADCAST_TRANSACTION("broadcastTransaction", BroadcastTransaction.instance),
    BROADCAST_SUPERNODE_TRANSACTION("broadcastSupernodeSignedTransaction", BroadcastSupernodeSignedTransaction.instance),
    BECOME_SUPERNODE("becomeSupernode", BecomeSupernode.instance),
    BLOCK_SHENANIGAN("blockByGuardNode", BlockByGuardNode.instance),
    GET_SUPERNODE_TRANSACTION("getSupernodeSignedTransaction", GetSupernodeSignedTransaction.instance),
    GET_WORK("getWork", GetWork.instance), GET_SUPERNODES("getSupernodes", GetSupernodes.instance), CALCULATE_FULL_HASH(
            "calculateFullHash",
            CalculateFullHash.instance), DECRYPT_FROM("decryptFrom", DecryptFrom.instance), DECODE_HALLMARK(
            "decodeHallmark",
            DecodeHallmark.instance), DECODE_TOKEN("decodeToken", DecodeToken.instance), DECODE_FILE_TOKEN(
            "decodeFileToken", DecodeFileToken.instance), DECODE_Q_R_CODE("decodeQRCode",
            DecodeQRCode.instance), ENCODE_Q_R_CODE("encodeQRCode",
            EncodeQRCode.instance), ENCRYPT_TO("encryptTo",
            EncryptTo.instance), EVENT_REGISTER("eventRegister",
            EventRegister.instance), EVENT_WAIT("eventWait",
            EventWait.instance), GENERATE_TOKEN("generateToken",
            GenerateToken.instance), GENERATE_FILE_TOKEN(
            "generateFileToken",
            GenerateFileToken.instance), GET_ACCOUNT(
            "getAccount",
            GetAccount.instance), GET_WORK_BOUNTIES(
            "getWorkBounties",
            GetWorkBounties.instance), LONGPOLL(
            "longpoll",
            Longpoll.instance), GET_ACCOUNT_BLOCK_COUNT(
            "getAccountBlockCount",
            GetAccountBlockCount.instance), GET_ACCOUNT_BLOCK_IDS(
            "getAccountBlockIds",
            GetAccountBlockIds.instance), GET_ACCOUNT_BLOCKS(
            "getAccountBlocks",
            GetAccountBlocks.instance), GET_ACCOUNT_ID(
            "getAccountId",
            GetAccountId.instance), GET_ACCOUNT_LEDGER(
            "getAccountLedger",
            GetAccountLedger.instance), GET_ACCOUNT_LEDGER_ENTRY(
            "getAccountLedgerEntry",
            GetAccountLedgerEntry.instance), GET_ACCOUNT_LESSORS(
            "getAccountLessors",
            GetAccountLessors.instance), GET_BALANCE(
            "getBalance",
            GetBalance.instance), GET_BLOCK(
            "getBlock",
            GetBlock.instance), GET_UNCL_REDEEMS(
            "getUnclaimedRedeems",
            GetUnclaimedRedeems.instance), GET_BLOCK_ID(
            "getBlockId",
            GetBlockId.instance), GET_BLOCKS(
            "getBlocks",
            GetBlocks.instance), GET_BLOCKCHAIN_STATUS(
            "getBlockchainStatus",
            GetBlockchainStatus.instance), GET_BLOCKCHAIN_TRANSACTIONS(
            "getBlockchainTransactions",
            GetBlockchainTransactions.instance), GET_REFERENCING_TRANSACTIONS(
            "getReferencingTransactions",
            GetReferencingTransactions.instance), GET_CONSTANTS(
            "getConstants",
            GetConstants.instance), GET_GUARANTEED_BALANCE(
            "getGuaranteedBalance",
            GetGuaranteedBalance.instance), GET_E_C_BLOCK(
            "getECBlock",
            GetECBlock.instance), GET_INBOUND_PEERS(
            "getInboundPeers",
            GetInboundPeers.instance), GET_PLUGINS(
            "getPlugins",
            GetPlugins.instance), GET_MY_INFO(
            "getMyInfo",
            GetMyInfo.instance), GET_PEER(
            "getPeer",
            GetPeer.instance), GET_PEERS(
            "getPeers",
            GetPeers.instance), GET_STATE(
            "getState",
            GetState.instance), GET_TIME(
            "getTime",
            GetTime.instance), GET_TRANSACTION(
            "getTransaction",
            GetTransaction.instance), GET_TRANSACTION_BYTES(
            "getTransactionBytes",
            GetTransactionBytes.instance), GET_UNCONFIRMED_TRANSACTION_IDS(
            "getUnconfirmedTransactionIds",
            GetUnconfirmedTransactionIds.instance), GET_UNCONFIRMED_TRANSACTIONS(
            "getUnconfirmedTransactions",
            GetUnconfirmedTransactions.instance), LEASE_BALANCE(
            "leaseBalance",
            LeaseBalance.instance), LONG_CONVERT(
            "longConvert",
            LongConvert.instance), HEX_CONVERT(
            "hexConvert",
            HexConvert.instance), MARK_HOST(
            "markHost",
            MarkHost.instance), PARSE_TRANSACTION(
            "parseTransaction",
            ParseTransaction.instance), RS_CONVERT(
            "rsConvert",
            RSConvert.instance), SEND_MONEY(
            "sendMoney",
            SendMoney.instance), SET_ACCOUNT_INFO(
            "setAccountInfo",
            SetAccountInfo.instance), SIGN_TRANSACTION(
            "signTransaction",
            SignTransaction.instance), START_FORGING(
            "startForging",
            StartForging.instance), STOP_FORGING(
            "stopForging",
            StopForging.instance), GET_FORGING(
            "getForging",
            GetForging.instance), SEARCH_ACCOUNTS(
            "searchAccounts",
            SearchAccounts.instance), CLEAR_UNCONFIRMED_TRANSACTIONS(
            "clearUnconfirmedTransactions",
            ClearUnconfirmedTransactions.instance), REQUEUE_UNCONFIRMED_TRANSACTIONS(
            "requeueUnconfirmedTransactions",
            RequeueUnconfirmedTransactions.instance), REBROADCAST_UNCONFIRMED_TRANSACTIONS(
            "rebroadcastUnconfirmedTransactions",
            RebroadcastUnconfirmedTransactions.instance), GET_ALL_WAITING_TRANSACTIONS(
            "getAllWaitingTransactions",
            GetAllWaitingTransactions.instance), GET_ALL_BROADCASTED_TRANSACTIONS(
            "getAllBroadcastedTransactions",
            GetAllBroadcastedTransactions.instance), FULL_RESET(
            "fullReset",
            FullReset.instance), POP_OFF(
            "popOff",
            PopOff.instance), SCAN(
            "scan",
            Scan.instance), ADD_PEER(
            "addPeer",
            AddPeer.instance), BLACKLIST_PEER(
            "blacklistPeer",
            BlacklistPeer.instance), DUMP_PEERS(
            "dumpPeers",
            DumpPeers.instance), GET_LOG(
            "getLog",
            GetLog.instance), GET_STACK_TRACES(
            "getStackTraces",
            GetStackTraces.instance), RETRIEVE_PRUNED_DATA(
            "retrievePrunedData",
            RetrievePrunedData.instance), RETRIEVE_PRUNED_TRANSACTION(
            "retrievePrunedTransaction",
            RetrievePrunedTransaction.instance), SET_LOGGING(
            "setLogging",
            SetLogging.instance), SHUTDOWN(
            "shutdown",
            Shutdown.instance), TRIM_DERIVED_TABLES(
            "trimDerivedTables",
            TrimDerivedTables.instance), HASH(
            "hash",
            Hash.instance),
    // LEAVE THIS OUT FOR NOW BHASH(
    // LEAVE THIS OUT FOR NOW "bountyAnnouncement",
    // LEAVE THIS OUT FOR NOW BountyAnnouncement.instance),
            FULL_HASH_TO_ID(
            "fullHashToId",
            FullHashToId.instance), DETECT_MIME_TYPE(
            "detectMimeType",
            DetectMimeType.instance), GET_SHARED_KEY(
            "getSharedKey",
            GetSharedKey.instance), SET_API_PROXY_PEER(
            "setAPIProxyPeer",
            SetAPIProxyPeer.instance), SEND_TRANSACTION(
            "sendTransaction",
            SendTransaction.instance), BLACKLIST_API_PROXY_PEER(
            "blacklistAPIProxyPeer",
            BlacklistAPIProxyPeer.instance), CREATE_WORK(
            "createWork",
            CreateWork.instance), CANCEL_WORK(
            "cancelWork",
            CancelWork.instance), CREATE_POX(
            "createPoX",
            ProofOfX.instance), CREATE_REDEEM(
            "redeem",
            Redeem.instance),
    // LEAVE THIS OUT FOR NOW HASHBOUNTY(
    // LEAVE THIS OUT FOR NOW "getApprovedBounties",
    // LEAVE THIS OUT FOR NOW GetApprovedBounties.instance),
            GET_ACCOUNT_WORK(
            "getAccountWork",
            GetAccountWork.instance), GET_ACCOUNT_EFFICIENCY(
            "getAccountWorkEfficiencyPlot",
            GetAccountWorkEfficiencyPlot.instance), GET_MINEABLE_WORK(
            "getMineableWork",
            GetMineableWork.instance);
    private static final Map<String, APIEnum> apiByName = new HashMap<>();

    static {
        for (final APIEnum api : APIEnum.values())
            if (APIEnum.apiByName.put(api.getName(), api) != null)
                throw new RuntimeException("Duplicate API name: " + api.getName());
    }

    public static EnumSet<APIEnum> base64StringToEnumSet(final String apiSetBase64) {
        final byte[] decoded = Base64.getDecoder().decode(apiSetBase64);
        final BitSet bs = BitSet.valueOf(decoded);
        final EnumSet<APIEnum> result = EnumSet.noneOf(APIEnum.class);
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            result.add(APIEnum.values()[i]);
            if (i == Integer.MAX_VALUE) break; // or (i+1) would overflow
        }
        return result;
    }

    public static String enumSetToBase64String(final EnumSet<APIEnum> apiSet) {
        final BitSet bitSet = new BitSet();
        for (final APIEnum api : apiSet) bitSet.set(api.ordinal());
        return Base64.getEncoder().encodeToString(bitSet.toByteArray());
    }

    public static APIEnum fromName(final String name) {
        return APIEnum.apiByName.get(name);
    }

    private final String name;

    private final APIServlet.APIRequestHandler handler;

    APIEnum(final String name, final APIServlet.APIRequestHandler handler) {
        this.name = name;
        this.handler = handler;
    }

    public APIServlet.APIRequestHandler getHandler() {
        return this.handler;
    }

    public String getName() {
        return this.name;
    }
}
