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
 * @depends {nrs.modals.js}
 */
var NRS = (function(NRS, $, undefined) {
	
    var address="";
    var amountNQT=0;
    var receipient="";

    var updateSignatureView = function() {
        var redeemEntry = $("#redeem_address").val();
        var res = redeemEntry.split(","); 
        console.log("LOADED REDEEM MODAL, entry = " + redeemEntry + ".");
        $("#redeem_address_field").html(res[1]);
        $("#redeem_amount_field").html(res[2]);
        $("#redeem_account_field").html(NRS.account);
        address=res[1];
        amountNQT = res[2];
        receipient = NRS.account;
        $("#receiver_id").val(receipient);
        $("#amountNQT").val(amountNQT);
    }

    var updateVisibles = function(){
        var redeemEntry = $("#redeem_type_selector").val();
        if(parseInt(redeemEntry) == 0){
            $("#hiddeable_group").show();
        }else{
            $("#hiddeable_group").hide()
        }
    }

	$("#redeem_modal").on("show.bs.modal", function() {
        document.getElementById("redeem_address").options.length = 0;
        NRS.sendRequest("getUnclaimedRedeems", {"nil": "nil"}, function(response) {
                            var x = document.getElementById("redeem_address");
                            if(response.redeems){
                                response.redeems.forEach(function(elem) {
                                    if(elem.indexOf("-")==-1){
                                        var option = document.createElement("option");
                                        option.value = elem;
                                        option.text = elem.split(",")[1] + ": " + NRS.formatAmount((elem.split(",")[2])) + " XEL" ;
                                        x.add(option);
                                    }else{
                                        console.log("Found multisig entry: "  + elem.split(",")[1] );
                                        var t = elem.split(",")[1].split("-");
                                        for(var i=1;i<t.length;++i){
                                            var option = document.createElement("option");
                                            option.value = elem;
                                            option.text = t[i] + ": " + NRS.formatAmount((elem.split(",")[2])) + " XEL   (" + t[0] + "-of-Multisig)";
                                            x.add(option);
                                        }
                                    }
                                });
                                var my_options = $("#redeem_address option");
                                var selected = $("#redeem_address").val();

                                my_options.sort(function(a,b) {
                                    if (a.text > b.text) return 1;
                                    if (a.text < b.text) return -1;
                                    return 0
                                })

                                $("#redeem_address").empty().append( my_options );
                                $("#redeem_address").val(selected);
                            }
                            updateSignatureView();

        });
        updateVisibles();
	});

    $("#redeem_address").on("change", function() {
		updateSignatureView();
	});

    $("#redeem_type_selector").on("change", function() {
        updateVisibles();
    });



	return NRS;
}(NRS || {}, jQuery));