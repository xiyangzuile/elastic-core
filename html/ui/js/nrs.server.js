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
function parseFormData(model){
  var formData = new FormData();

  function parseArray(arrayKey, arrayValue){
    if ($.type(arrayValue) !== undefined) {
      if($.type(arrayValue[0]) === 'string'){
        parseAttribute(arrayKey, arrayValue)
      } else {
        $.each(arrayValue, function loopArray(attributeKey, attributeValue) {
          parseModel(arrayKey+'['+attributeKey+']', attributeValue);
        });
      }
    }
  }

  function parseObject(objectKey, objectValue){
    if ($.type(objectValue) !== undefined) {
      $.each(objectValue, function loopObject(attributeKey, attributeValue) {
        parseAttribute(objectKey+'['+attributeKey+']', attributeValue);
      });
    }
  }

  function parseAttribute(attributeKey, attributeValue){

    if (attributeValue !== undefined ) {
      if($.type(attributeValue) === 'array' && $.type(attributeValue[0]) !== 'string'){
        parseArray(attributeKey, attributeValue)
      }
      else if($.isPlainObject(attributeValue) ){
        parseObject(attributeKey, attributeValue)
      }
      else {
        formData.append(attributeKey, attributeValue);
      }
    } 
  }

  function parseModel(keyString, model){
    $.each(model, function loopModel(inputKey, inputValue) {
      parseAttribute(keyString !== '' ? keyString+'['+inputKey+']' : inputKey, inputValue);
    });
  }

  parseModel('', model);

  return formData;
}
var NRS = (function (NRS, $, undefined) {
    var _password;



    NRS.setServerPassword = function (password) {
        _password = password;
    };

    NRS.sendOutsideRequest = function (url, data, callback, async) {
        if ($.isFunction(data)) {
            async = callback;
            callback = data;
            data = {};
        } else {
            data = data || {};
        }

        $.support.cors = true;

        $.ajax({
            url: url,
            crossDomain: true,
            dataType: "json",
            type: "GET",
            timeout: 30000,
            async: (async === undefined ? true : async),
            data: data
        }).done(function (json) {
            //why is this necessary??..
            if (json.errorCode && !json.errorDescription) {
                json.errorDescription = (json.errorMessage ? json.errorMessage : $.t("server_error_unknown"));
            }
            if (callback) {
                callback(json, data);
            }
        }).fail(function (xhr, textStatus, error) {
            if (callback) {
                callback({
                    "errorCode": -1,
                    "errorDescription": error
                }, {});
            }
        });
    };

    NRS.sendRequest = function (requestType, data, callback, isAsync, noProxy) {
        if (requestType == undefined) {
            NRS.logConsole("Undefined request type");
            return;
        }
        if (!NRS.isRequestTypeEnabled(requestType)) {
            callback({
                "errorCode": 1,
                "errorDescription": $.t("request_of_type", {
                    type: requestType
                })
            });
            return;
        }
        if (data == undefined) {
            NRS.logConsole("Undefined data for " + requestType);
            return;
        }
        if (callback == undefined) {
            NRS.logConsole("Undefined callback function for " + requestType);
            return;
        }

        $.each(data, function (key, val) {
            if (key != "secretPhrase") {
                if (typeof val == "string") {
                    data[key] = $.trim(val);
                }
            }
        });
        //convert NXT to NQT...
        var field = "N/A";
        try {
            var nxtFields = [
                ["feeNXT", "feeNQT"],
                ["amountNXT", "amountNQT"],
                ["priceNXT", "priceNQT"],
                ["refundNXT", "refundNQT"],
                ["discountNXT", "discountNQT"],
                ["phasingQuorumNXT", "phasingQuorum"],
                ["phasingMinBalanceNXT", "phasingMinBalance"],
                ["controlQuorumNXT", "controlQuorum"],
                ["controlMinBalanceNXT", "controlMinBalance"],
                ["controlMaxFeesNXT", "controlMaxFees"],
                ["minBalanceNXT", "minBalance"],
                ["shufflingAmountNXT", "amount"],
                ["monitorAmountNXT", "amount"],
                ["monitorThresholdNXT", "threshold"]
            ];

            for (i = 0; i < nxtFields.length; i++) {
                var nxtField = nxtFields[i][0];
                var nqtField = nxtFields[i][1];
                if (nxtField in data) {
                    data[nqtField] = NRS.convertToNQT(data[nxtField]);
                    delete data[nxtField];
                }
            }
        } catch (err) {
            callback({
                "errorCode": 1,
                "errorDescription": err + " (" + $.t(field) + ")"
            });
            return;
        }
        // convert asset/currency decimal amount to base unit
        try {
            var currencyFields = [
                ["phasingQuorumQNTf", "phasingHoldingDecimals"],
                ["phasingMinBalanceQNTf", "phasingHoldingDecimals"],
                ["controlQuorumQNTf", "controlHoldingDecimals"],
                ["controlMinBalanceQNTf", "controlHoldingDecimals"],
                ["minBalanceQNTf", "create_poll_asset_decimals"],
                ["minBalanceQNTf", "create_poll_ms_decimals"],
                ["amountQNTf", "shuffling_asset_decimals"],
                ["amountQNTf", "shuffling_ms_decimals"]
            ];
            var toDelete = [];
            for (i = 0; i < currencyFields.length; i++) {
                var decimalUnitField = currencyFields[i][0];
                var decimalsField = currencyFields[i][1];
                field = decimalUnitField.replace("QNTf", "");

                if (decimalUnitField in data && decimalsField in data) {
                    data[field] = NRS.convertToQNT(parseFloat(data[decimalUnitField]), parseInt(data[decimalsField]));
                    toDelete.push(decimalUnitField);
                    toDelete.push(decimalsField);
                }
            }
            for (var i = 0; i < toDelete.length; i++) {
                delete data[toDelete[i]];
            }
        } catch (err) {
            callback({
                "errorCode": 1,
                "errorDescription": err + " (" + $.t(field) + ")"
            });
            return;
        }

        //Fill phasing parameters when mandatory approval is enabled
        if (requestType != "approveTransaction"
            && NRS.accountInfo.accountControls && $.inArray('PHASING_ONLY', NRS.accountInfo.accountControls) > -1
                && NRS.accountInfo.phasingOnly
                && NRS.accountInfo.phasingOnly.votingModel >= 0) {

            var phasingControl = NRS.accountInfo.phasingOnly;
            var maxFees = new BigInteger(phasingControl.maxFees);
            if (maxFees > 0 && new BigInteger(data.feeNQT).compareTo(new BigInteger(phasingControl.maxFees)) > 0) {
                callback({
                    "errorCode": 1,
                    "errorDescription": $.t("error_fee_exceeds_max_account_control_fee", {
                        "maxFee": NRS.convertToNXT(phasingControl.maxFees)
                    })
                });
                return;
            }
            var phasingDuration = parseInt(data.phasingFinishHeight) - NRS.lastBlockHeight;
            var minDuration = parseInt(phasingControl.minDuration) > 0 ? parseInt(phasingControl.minDuration) : 0;
            var maxDuration = parseInt(phasingControl.maxDuration) > 0 ? parseInt(phasingControl.maxDuration) : NRS.constants.SERVER.maxPhasingDuration;

            if (phasingDuration < minDuration || phasingDuration > maxDuration) {
                callback({
                    "errorCode": 1,
                    "errorDescription": $.t("error_finish_height_out_of_account_control_interval", {
                        "min": NRS.lastBlockHeight + minDuration,
                        "max": NRS.lastBlockHeight + maxDuration
                    })
                });
                return;
            }

            var phasingParams = NRS.phasingControlObjectToPhasingParams(phasingControl);
            $.extend(data, phasingParams);
            data.phased = true;

            delete data.phasingHashedSecret;
            delete data.phasingHashedSecretAlgorithm;
            delete data.phasingLinkedFullHash;
        }

        if (!data.recipientPublicKey) {
            delete data.recipientPublicKey;
        }
        if (!data.referencedTransactionFullHash) {
            delete data.referencedTransactionFullHash;
        }

        //gets account id from passphrase client side, used only for login.
        var accountId;
        if (requestType == "getAccountId") {
            accountId = NRS.getAccountId(data.secretPhrase);

            var nxtAddress = new NxtAddress();
            var accountRS = "";
            if (nxtAddress.set(accountId)) {
                accountRS = nxtAddress.toString();
            }
            callback({
                "account": accountId,
                "accountRS": accountRS
            });
            return;
        }

        //check to see if secretPhrase supplied matches logged in account, if not - show error.
        if ("secretPhrase" in data) {
            accountId = NRS.getAccountId(NRS.rememberPassword ? _password : data.secretPhrase);
            if (accountId != NRS.account && !data.calculateFee) {
                callback({
                    "errorCode": 1,
                    "errorDescription": $.t("error_passphrase_incorrect")
                });
            } else {
                //ok, accountId matches..continue with the real request.
                NRS.processAjaxRequest(requestType, data, callback, isAsync, noProxy);
            }
        } else {
            NRS.processAjaxRequest(requestType, data, callback, isAsync, noProxy);
        }
    };

    function isVolatileRequest(doNotSign, type, requestType) {
        return (!NRS.isLocalHost || doNotSign || NRS.state.apiProxy) && type == "POST" && !NRS.isSubmitPassphrase(requestType);
    }

    NRS.processAjaxRequest = function (requestType, data, callback, isAsync, noProxy) {
        var extra = null;
        if (data["_extra"]) {
            extra = data["_extra"];
            delete data["_extra"];
        }
        var currentPage = null;
        var currentSubPage = null;

        //means it is a page request, not a global request.. Page requests can be aborted.
        if (requestType.slice(-1) == "+") {
            requestType = requestType.slice(0, -1);
            currentPage = NRS.currentPage;
        } else {
            //not really necessary... we can just use the above code..
            var plusCharacter = requestType.indexOf("+");

            if (plusCharacter > 0) {
                requestType = requestType.substr(0, plusCharacter);
                currentPage = NRS.currentPage;
            }
        }

        if (currentPage && NRS.currentSubPage) {
            currentSubPage = NRS.currentSubPage;
        }

        var type = (NRS.isRequirePost(requestType) || "secretPhrase" in data || "doNotSign" in data || "adminPassword" in data || ("source_code" in data) ? "POST" : "GET");
        var url = NRS.getRequestPath();
        if (noProxy) {
            url = "/nxt";
        }
        url += "?requestType=" + requestType;

        var subtype = ("source_code" in data) ? "MULTIPART" : "";

        if (type == "GET") {
            if (typeof data == "string") {
                data += "&random=" + Math.random();
            } else {
                data.random = Math.random();
            }
        }

        if (type == "POST" && NRS.isRequireBlockchain(requestType) && requestType!="redeem" && NRS.accountInfo.errorCode && NRS.accountInfo.errorCode == 5) {
            callback({
                "errorCode": 2,
                "errorDescription": $.t("error_new_account")
            }, data);
            return;
        }

        if (data.referencedTransactionFullHash) {
            if (!/^[a-z0-9]{64}$/.test(data.referencedTransactionFullHash)) {
                callback({
                    "errorCode": -1,
                    "errorDescription": $.t("error_invalid_referenced_transaction_hash")
                }, data);
                return;
            }
        }

        var secretPhrase = "";
        var isVolatile = isVolatileRequest(data.doNotSign, type, requestType);
        if (isVolatile) {
            if (NRS.rememberPassword) {
                secretPhrase = _password;
            } else {
                secretPhrase = data.secretPhrase;
            }

            delete data.secretPhrase;

            if (NRS.accountInfo && NRS.accountInfo.publicKey) {
                data.publicKey = NRS.accountInfo.publicKey;
            } else if (!data.doNotSign && secretPhrase) {
                data.publicKey = NRS.generatePublicKey(secretPhrase);
                NRS.accountInfo.publicKey = data.publicKey;
            }
            var ecBlock = NRS.getECBlock(NRS.isTestNet);
            data.ecBlockId = ecBlock.id;
            data.ecBlockHeight = ecBlock.height;
        } else if (type == "POST" && NRS.rememberPassword) {
            data.secretPhrase = _password;
        }

        $.support.cors = true;
        // Used for passing row query string which is too long for a GET request
        if (data.querystring) {
            data = data.querystring;
            type = "POST";
        }
        var contentType;
        var processData;
        var formData = null;
        
        var config = NRS.getFileUploadConfig(requestType, data);
        if (config && $(config.selector)[0].files[0]) {
            // inspired by http://stackoverflow.com/questions/5392344/sending-multipart-formdata-with-jquery-ajax
            contentType = false;
            processData = false;
            formData = new FormData();
            var file;
            if (data.messageFile) {
                file = data.messageFile;
                delete data.messageFile;
                delete data.encrypt_message;
            } else {
                file = $(config.selector)[0].files[0];
            }
            if (!file && requestType == "uploadTaggedData") {
                callback({
                    "errorCode": 3,
                    "errorDescription": $.t("error_no_file_chosen")
                }, data);
                return;
            }
            if (file && file.size > config.maxSize) {
                callback({
                    "errorCode": 3,
                    "errorDescription": $.t(config.errorDescription, {
                        "size": file.size,
                        "allowed": config.maxSize
                    })
                }, data);
                return;
            }
            type = "POST";
            formData.append(config.requestParam, file);
            for (var key in data) {
                if (!data.hasOwnProperty(key)) {
                    continue;
                }
                formData.append(key, data[key]);
            }
        } else {
            // JQuery defaults
            contentType = "application/x-www-form-urlencoded; charset=UTF-8";
            processData = true;
        }

        if(subtype == "MULTIPART"){
            processData = false;
        }

        var callDict = {
            url: url,
            crossDomain: true,
            dataType: "json",
            type: type,
            timeout: 30000,
            async: (isAsync === undefined ? true : isAsync),
            currentPage: currentPage,
            currentSubPage: currentSubPage,
            shouldRetry: (type == "GET" ? 2 : undefined),
            traditional: true,
            data: (formData != null ? formData : (subtype=="MULTIPART")?parseFormData(data):data),
            contentType: contentType,
            processData: processData
        };
        if (type=="POST" && subtype=="MULTIPART"){
            callDict["contentType"]=false;
            callDict["cache"]=false;
        }
        console.log(callDict);
        $.ajax(callDict).done(function (response) {
            NRS.escapeResponseObjStrings(response);
            if (NRS.console) {
                NRS.addToConsole(this.url, this.type, this.data, response);
            }
            addAddressData(data);
            if (secretPhrase && response.unsignedTransactionBytes && !data.doNotSign && !response.errorCode && !response.error) {
                var publicKey = NRS.generatePublicKey(secretPhrase);
                var signature = NRS.signBytes(response.unsignedTransactionBytes, converters.stringToHexString(secretPhrase));

                if (!NRS.verifySignature(signature, response.unsignedTransactionBytes, publicKey, callback)) {
                    return;
                }
                addMissingData(data);
                if (file) {
                    var r = new FileReader();
                    r.onload = function (e) {
                        data.filebytes = e.target.result;
                        data.filename = file.name;
                        NRS.verifyAndSignTransactionBytes(response.unsignedTransactionBytes, signature, requestType, data, callback, response, extra, isVolatile);
                    };
                    r.readAsArrayBuffer(file);
                } else {
                    NRS.verifyAndSignTransactionBytes(response.unsignedTransactionBytes, signature, requestType, data, callback, response, extra, isVolatile);
                }
            } else {
                if (response.errorCode || response.errorDescription || response.errorMessage || response.error) {
                    response.errorDescription = NRS.translateServerError(response);
                    delete response.fullHash;
                    if (!response.errorCode) {
                        response.errorCode = -1;
                    }
                    callback(response, data);
                } else {
                    if (response.broadcasted == false && !data.calculateFee) {
                        async.waterfall([
                            function (callback) {
                                addMissingData(data);
                                if (!response.unsignedTransactionBytes) {
                                    callback(null);
                                }
                                if (file) {
                                    var r = new FileReader();
                                    r.onload = function (e) {
                                        data.filebytes = e.target.result;
                                        data.filename = file.name;
                                        callback(null);
                                    };
                                    r.readAsArrayBuffer(file);
                                } else {
                                    callback(null);
                                }
                            },
                            function (callback) {
                                // We do not want the byte validation stuff here
                                // TODO: redo some time later
                                
                                if (response.unsignedTransactionBytes &&
                                    !NRS.verifyTransactionBytes(converters.hexStringToByteArray(response.unsignedTransactionBytes), requestType, data, response.transactionJSON.attachment, isVolatile)) {
                                    callback({
                                        "errorCode": 1,
                                        "errorDescription": $.t("error_bytes_validation_server")
                                    }, data);
                                    return;
                                }
                                callback(null);
                            }
                        ], function () {
                            NRS.showRawTransactionModal(response);
                        });
                    } else {
                        if (extra) {
                            data["_extra"] = extra;
                        }
                        callback(response, data);
                        if (data.referencedTransactionFullHash && !response.errorCode) {
                            $.growl($.t("info_referenced_transaction_hash"), {
                                "type": "info"
                            });
                        }
                    }
                }
            }
        }).fail(function (xhr, textStatus, error) {
            if (NRS.console) {
                NRS.addToConsole(this.url, this.type, this.data, error, true);
            }

            if ((error == "error" || textStatus == "error") && (xhr.status == 404 || xhr.status == 0)) {
                if (type == "POST") {
                    $.growl($.t("error_server_connect"), {
                        "type": "danger",
                        "offset": 10
                    });
                }
            }

            if (error != "abort") {
                if (error == "timeout") {
                    error = $.t("error_request_timeout");
                }
                callback({
                    "errorCode": -1,
                    "errorDescription": error
                }, {});
            }
        });
    };

    NRS.verifyAndSignTransactionBytes = function (transactionBytes, signature, requestType, data, callback, response, extra, isVerifyECBlock) {
        var byteArray = converters.hexStringToByteArray(transactionBytes);
        if (!NRS.verifyTransactionBytes(byteArray, requestType, data, response.transactionJSON.attachment, isVerifyECBlock)) {
            callback({
                "errorCode": 1,
                "errorDescription": $.t("error_bytes_validation_server")
            }, data);
            return;
        }
        var payload = transactionBytes.substr(0, 192) + signature + transactionBytes.substr(320);
        if (data.broadcast == "false") {
            response.transactionBytes = payload;
            response.transactionJSON.signature = signature;
            NRS.showRawTransactionModal(response);
        } else {
            if (extra) {
                data["_extra"] = extra;
            }
            NRS.broadcastTransactionBytes(payload, callback, response, data);
        }
    };

    NRS.verifyTransactionBytes = function (byteArray, requestType, data, attachment, isVerifyECBlock) {
        var transaction = {};
        transaction.type = byteArray[0];
        transaction.version = (byteArray[1] & 0xF0) >> 4;
        transaction.subtype = byteArray[1] & 0x0F;
        transaction.timestamp = String(converters.byteArrayToSignedInt32(byteArray, 2));
        transaction.deadline = String(converters.byteArrayToSignedShort(byteArray, 6));
        transaction.publicKey = converters.byteArrayToHexString(byteArray.slice(8, 40));
        transaction.recipient = String(converters.byteArrayToBigInteger(byteArray, 40));
        transaction.amountNQT = String(converters.byteArrayToBigInteger(byteArray, 48));
        transaction.feeNQT = String(converters.byteArrayToBigInteger(byteArray, 56));

        // Here, slice the original signature as well as the new SN signature
        var refHash = byteArray.slice(64, 96 + 32);
        transaction.referencedTransactionFullHash = converters.byteArrayToHexString(refHash);
        if (transaction.referencedTransactionFullHash == "0000000000000000000000000000000000000000000000000000000000000000") {
            transaction.referencedTransactionFullHash = "";
        }
        transaction.flags = 0;
        if (transaction.version > 0) {
            transaction.flags = converters.byteArrayToSignedInt32(byteArray, 160);
            transaction.ecBlockHeight = String(converters.byteArrayToSignedInt32(byteArray, 164));
            transaction.ecBlockId = String(converters.byteArrayToBigInteger(byteArray, 168));
            if (isVerifyECBlock) {
                var ecBlock = NRS.getECBlock(NRS.isTestNet);
                if (transaction.ecBlockHeight != ecBlock.height) {
                	console.log("Crippled TX Verification: ecBlockHeight not matching");
                    return false;
                }
                if (transaction.ecBlockId != ecBlock.id) {
                	console.log("Crippled TX Verification: ecBlockId not matching");
                    return false;
                }
            }
        }

        if (transaction.publicKey != NRS.accountInfo.publicKey && transaction.publicKey != data.publicKey) {
        	console.log("Crippled TX Verification: bad programmer screwed up the pubkey check");
            return false;
        }

        if (transaction.deadline !== data.deadline) {
        	console.log("Crippled TX Verification: crook added wrong deadline");
            return false;
        }

        if (transaction.recipient !== data.recipient) {
            if ((data.recipient == NRS.constants.GENESIS || data.recipient == "") && transaction.recipient == "0") {
                //ok
            } else {
            	console.log("Crippled TX Verification: receipient now sucks, didnn't before");
                return false;
            }
        }

        if (transaction.amountNQT !== data.amountNQT) {
        	console.log("Crippled TX Verification: amountNQT not matching");
            return false;
        }

        if ("referencedTransactionFullHash" in data) {
            if (transaction.referencedTransactionFullHash !== data.referencedTransactionFullHash) {
            	console.log("Crippled TX Verification: problem in referenced TX hash");
                return false;
            }
        } else if (transaction.referencedTransactionFullHash !== "") {
            console.log("Crippled TX Verification: problem in referenced TX hash #2");
            return false;
        }
        var pos;
        if (transaction.version > 0) {
            //has empty attachment, so no attachmentVersion byte...
            if (requestType == "sendMoney" || requestType == "sendMessage") {
                pos = 176;
            } else {
                pos = 177;
            }
        } else {
            pos = 160;
        }
        return NRS.verifyTransactionTypes(byteArray, transaction, requestType, data, pos, attachment);
    };

    NRS.verifyTransactionTypes = function (byteArray, transaction, requestType, data, pos, attachment) {
        //  @community, please check if its okay to remove all checks here!
        // I would say ... screw them!

        return true;
    };

    NRS.broadcastTransactionBytes = function (transactionData, callback, originalResponse, originalData) {
        var requestType = NRS.state.apiProxy ? "sendTransaction": "broadcastTransaction";
        $.ajax({
            url: NRS.getRequestPath() + "?requestType=" + requestType,
            crossDomain: true,
            dataType: "json",
            type: "POST",
            timeout: 30000,
            async: true,
            data: {
                "transactionBytes": transactionData,
                "prunableAttachmentJSON": JSON.stringify(originalResponse.transactionJSON.attachment),
                "adminPassword": NRS.settings.admin_password
            }
        }).done(function (response) {
            NRS.escapeResponseObjStrings(response);
            if (NRS.console) {
                NRS.addToConsole(this.url, this.type, this.data, response);
            }

            if (response.errorCode) {
                if (!response.errorDescription) {
                    response.errorDescription = (response.errorMessage ? response.errorMessage : "Unknown error occurred.");
                }
                callback(response, originalData);
            } else if (response.error) {
                response.errorCode = 1;
                response.errorDescription = response.error;
                callback(response, originalData);
            } else {
                if ("transactionBytes" in originalResponse) {
                    delete originalResponse.transactionBytes;
                }
                originalResponse.broadcasted = true;
                originalResponse.transaction = response.transaction;
                originalResponse.fullHash = response.fullHash;
                callback(originalResponse, originalData);
                if (originalData.referencedTransactionFullHash) {
                    $.growl($.t("info_referenced_transaction_hash"), {
                        "type": "info"
                    });
                }
            }
        }).fail(function (xhr, textStatus, error) {
            if (NRS.console) {
                NRS.addToConsole(this.url, this.type, this.data, error, true);
            }

            if (error == "timeout") {
                error = $.t("error_request_timeout");
            }
            callback({
                "errorCode": -1,
                "errorDescription": error
            }, {});
        });
    };
    
    NRS.sendRequestQRCode = function(target, qrCodeData, width, height) {
        width = width || 0;
        height = height || 0;
        NRS.sendRequest("encodeQRCode",
            {
                "qrCodeData": qrCodeData,
                "width": width,
                "height": height
            },
            function(response) {
                if('qrCodeBase64' in response) {
                    $(target).empty().append(
                        $("<img src='data:image/jpeg;base64,"+response.qrCodeBase64+"'>")
                    );
                }
            },
            true
        );
    };

    function addAddressData(data) {
        if (typeof data == "object" && ("recipient" in data)) {
            var address = new NxtAddress();
            if (/^XEL\-/i.test(data.recipient)) {
                data.recipientRS = data.recipient;
                if (address.set(data.recipient)) {
                    data.recipient = address.account_id();
                }
            } else {
                if (address.set(data.recipient)) {
                    data.recipientRS = address.toString();
                }
            }
        }
    }

    function addMissingData(data) {
        if (!("amountNQT" in data)) {
            data.amountNQT = "0";
        }
        if (!("recipient" in data)) {
            data.recipient = NRS.constants.GENESIS;
            data.recipientRS = NRS.constants.GENESIS_RS;
        }
    }

    function validateCommonPhasingData(byteArray, pos, data, prefix) {
        if (byteArray[pos] != (parseInt(data[prefix + "VotingModel"]) & 0xFF)) {
            return -1;
        }
        pos++;
        var quorum = String(converters.byteArrayToBigInteger(byteArray, pos));
        if (quorum !== "0" && quorum !== String(data[prefix + "Quorum"])) {
            return -1;
        }
        pos += 8;
        var minBalance = String(converters.byteArrayToBigInteger(byteArray, pos));
        if (minBalance !== "0" && minBalance !== data[prefix + "MinBalance"]) {
            return -1;
        }
        pos += 8;
        var whiteListLength = byteArray[pos];
        pos++;
        for (i = 0; i < whiteListLength; i++) {
            var accountId = converters.byteArrayToBigInteger(byteArray, pos);
            var accountRS = NRS.convertNumericToRSAccountFormat(accountId);
            pos += 8;
            if (String(accountId) !== data[prefix + "Whitelisted"][i] && String(accountRS) !== data[prefix + "Whitelisted"][i]) {
                return -1;
            }
        }
        var holdingId = String(converters.byteArrayToBigInteger(byteArray, pos));
        if (holdingId !== "0" && holdingId !== data[prefix + "Holding"]) {
            return -1;
        }
        pos += 8;
        var minBalanceModel = String(byteArray[pos]);
        if (minBalanceModel !== "0" && minBalanceModel !== String(data[prefix + "MinBalanceModel"])) {
            return -1;
        }
        pos++;
        return pos;
    }

    return NRS;
}(NRS || {}, jQuery));