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
                }
            }
        },
        1: {
            'title': "Messaging/Voting/Aliases",
            'i18nKeyTitle': 'messaging_voting_aliases',
            'iconHTML': "<i class='fa fa-envelope-square'></i>",
            'subTypes': {
                0: {
                    'title': "Arbitrary Message",
                    'i18nKeyTitle': 'arbitrary_message',
                    'iconHTML': "<i class='fa fa-envelope-o'></i>",
                    'receiverPage': 'messages'
                },
                1: {
                    'title': "Hub Announcement",
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