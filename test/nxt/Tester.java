/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;

@SuppressWarnings("unused")
public class Tester {
    private final String secretPhrase;
    private final byte[] privateKey;
    private final byte[] publicKey;
    private final String publicKeyStr;
    private final long id;
    private final String strId;
    private final String rsAccount;
    private final long initialBalance;
    private final long initialUnconfirmedBalance;
    private final long initialEffectiveBalance;
   
    public Tester(String secretPhrase) {
        this.secretPhrase = secretPhrase;
        this.privateKey = Crypto.getPrivateKey(secretPhrase);
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.publicKeyStr = Convert.toHexString(publicKey);
        this.id = Account.getId(publicKey);
        this.strId = Long.toUnsignedString(id);
        this.rsAccount = Convert.rsAccount(id);
        Account account = Account.getAccount(publicKey);
        if (account != null) {
            this.initialBalance = account.getBalanceNQT();
            this.initialUnconfirmedBalance = account.getUnconfirmedBalanceNQT();
            this.initialEffectiveBalance = account.getEffectiveBalanceNXT();
        } else {
            initialBalance = 0;
            initialUnconfirmedBalance = 0;
            initialEffectiveBalance = 0;
        }
    }

    public String getSecretPhrase() {
        return secretPhrase;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public String getPublicKeyStr() {
        return publicKeyStr;
    }

    public Account getAccount() {
        return Account.getAccount(publicKey);
    }

    public long getId() {
        return id;
    }

    public String getStrId() {
        return strId;
    }

    public String getRsAccount() {
        return rsAccount;
    }

    public long getBalanceDiff() {
        return Account.getAccount(id).getBalanceNQT() - initialBalance;
    }

    public long getUnconfirmedBalanceDiff() {
        return Account.getAccount(id).getUnconfirmedBalanceNQT() - initialUnconfirmedBalance;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

    public long getBalance() {
        return getAccount().getBalanceNQT();
    }

   

    public long getInitialUnconfirmedBalance() {
        return initialUnconfirmedBalance;
    }

    public long getInitialEffectiveBalance() {
        return initialEffectiveBalance;
    }

}