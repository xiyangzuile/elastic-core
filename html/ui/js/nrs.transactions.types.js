/******************************************************************************
 * Copyright © 2016-2017 The XEL Core Developers.                             *
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

/**
 * @depends {nrs.js}
 */
var NRS = (function(NRS, $, undefined) {
    // If you add new mandatory attributes, please make sure to add them to
    // NRS.loadTransactionTypeConstants as well (below)
    NRS.transactionTypes = {
        0: {
            'title': "Payment",
            'i18nKeyTitle': 'payment',
            'iconHTML': "<i class='ion-calculator'></i>",
            'subTypes': {
                0: {
                    'title': "Ordinary Payment",
                    'i18nKeyTitle': 'ordinary_payment',
                    'iconHTML': "<i class='fa fa-money'></i>",
                    'receiverPage': 'transactions'
                },
                1: {
                    'title': "Redeem from Genesis",
                    'i18nKeyTitle': 'redeem_payment',
                    'iconHTML': "<i class='fa fa-money'></i>",
                    'receiverPage': 'transactions'
                }
            }
        },
        1: {
            'title': "Messaging/Announcements",
            'i18nKeyTitle': 'messaging_voting_aliases',
            'iconHTML': "<i class='fa fa-envelope-square'></i>",
            'subTypes': {
                1: {
                    'title': "Supernode Announcement",
                    'i18nKeyTitle': 'hub_announcement',
                    'iconHTML': "<i class='ion-radio-waves'></i>"
                },
                2: {
                    'title': "Account Info",
                    'i18nKeyTitle': 'account_info',
                    'iconHTML': "<i class='fa fa-info'></i>"
                }
            }
        },
        2: {
            'title': "Account Control",
            'i18nKeyTitle': 'account_control',
            'iconHTML': '<i class="ion-locked"></i>',
            'subTypes': {
                0: {
                    'title': "Balance Leasing",
                    'i18nKeyTitle': 'balance_leasing',
                    'iconHTML': '<i class="fa fa-arrow-circle-o-right"></i>',
                    'receiverPage': "transactions"
                }
            }
        },
        3: {
            'title': "Work Management",
            'i18nKeyTitle': 'work_control',
            'iconHTML': '<i class="fa fa-database"></i>',
            'subTypes': {
                0: {
                    'title': "Work Creation",
                    'i18nKeyTitle': 'work_creation',
                    'iconHTML': '<i class="fa fa-plus-circle"></i>',
                    'receiverPage': "myownwork"
                },
                2: {
                    'title': "Proof Of Work",
                    'i18nKeyTitle': 'proof_of_work',
                    'iconHTML': '<i class="fa fa-legal"></i>',
                    'receiverPage': "myownwork"
                },
                3: {
                    'title': "Bounty",
                    'i18nKeyTitle': 'bounty',
                    'iconHTML': '<i class="fa fa-circle-thin"></i>',
                    'receiverPage': "myownwork"
                },
                4: {
                    'title': "Bounty Announcement",
                    'i18nKeyTitle': 'bounty_announcement',
                    'iconHTML': '<i class="fa fa-spinner"></i>',
                    'receiverPage': "myownwork"
                },
                5: {
                    'title': "Work Cancellation Request",
                    'i18nKeyTitle': 'work_cancellation_r',
                    'iconHTML': '<i class="fa fa-spinner"></i>',
                    'receiverPage': "myownwork"
                }
            }
        }
    };

    NRS.subtype = {};

    NRS.loadTransactionTypeConstants = function(response) {
        if (response.genesisAccountId) {
            $.each(response.transactionTypes, function(typeIndex, type) {
                if (!(typeIndex in NRS.transactionTypes)) {
                    NRS.transactionTypes[typeIndex] = {
                        'title': "Unknown",
                        'i18nKeyTitle': 'unknown',
                        'iconHTML': '<i class="fa fa-question-circle"></i>',
                        'subTypes': {}
                    }
                }
                $.each(type.subtypes, function(subTypeIndex, subType) {
                    if (!(subTypeIndex in NRS.transactionTypes[typeIndex]["subTypes"])) {
                        NRS.transactionTypes[typeIndex]["subTypes"][subTypeIndex] = {
                            'title': "Unknown",
                            'i18nKeyTitle': 'unknown',
                            'iconHTML': '<i class="fa fa-question-circle"></i>'
                        }
                    }
                    NRS.transactionTypes[typeIndex]["subTypes"][subTypeIndex]["serverConstants"] = subType;
                });
            });
            NRS.subtype = response.transactionSubTypes;
        }
    };

    NRS.isOfType = function(transaction, type_str) {
        if (!NRS.subtype[type_str]) {
            $.growl($.t("unsupported_transaction_type"));
            return;
        }
        return transaction.type == NRS.subtype[type_str].type && transaction.subtype == NRS.subtype[type_str].subtype;
    };
    
    return NRS;
}(NRS || {}, jQuery));