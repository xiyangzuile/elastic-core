/**
 * @depends {nrs.js}
 */
 function formatBytes(bytes,decimals) {
   if(bytes == 0) return '0 Byte';
   var k = 1000; // or 1024 for binary
   var dm = decimals + 1 || 3;
   var sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
   var i = Math.floor(Math.log(bytes) / Math.log(k));
   return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}



function isNumeric(n) {
  return !isNaN(parseFloat(n)) && isFinite(n);
}
var NRS = (function(NRS, $, undefined) {
	
	NRS.setup.myownwork = function() {
		var sidebarId = 'work_link';
		var options = {
			"id": sidebarId,
			"titleHTML": '<i class="fa fa-database"></i> <span data-i18n="work_control">Work Control</span>',
			"page": 'work',
			"desiredPosition": 60
		};
		NRS.addTreeviewSidebarMenuItem(options);
		options = {
			"titleHTML": '<span data-i18n="work_author">Work Management</span>',
			"type": 'PAGE',
			"page": 'myownwork'
		};
		NRS.appendMenuItemToTSMenuItem(sidebarId, options);
		options = {
			"titleHTML": '<span data-i18n="work_worker">Mining Overview</span>',
			"type": 'PAGE',
			"page": 'myforeignwork'
		};
		NRS.appendMenuItemToTSMenuItem(sidebarId, options);
	};




	// At first register a modal hook to fill default values
	// in work creation modals, and reset the source code upload form
	$("#new_work_modal").on("show.bs.modal", function(e) {
		$("#send_money_amount_creation").val("100"); // 100 is the default here
		$("#send_money_amount_creation").trigger("change");
		console.log("Work modal opened!");
	});


	var newworkrow = "<a href='#' data-target='#new_work_modal' data-toggle='modal' class='list-group-item larger-sidebar-element grayadder'>";
		newworkrow += "<i class='fa fa-edit work-image-type fa-5x'></i><p class='composelabel'>Click here to compose a new job</p>";
		newworkrow += "</a>";

	var _work = [];
	var _workToIndex = {};
	var computation_power=[];
	var solution_rate=[];
	var globalWorkItem = 0;
	var globalType = "30";
	var workItem = null;

	// deploy listener for amount text field
	var elem = $("#send_money_amount_creation");

	// Save current value of element
	elem.data('oldVal', elem.val());

	// Look for changes in the value
	elem.bind("propertychange change click keyup input paste", function(event){
			console.log("FAKE IT! FAKE IT!");

	// If value has changed...
	if (elem.data('oldVal') != elem.val()) {
	// Updated stored value
	    elem.data('oldVal', elem.val());
	    if(isNumeric(elem.val())){
	    	try{
          // This is our slick rounding_error_save_XEL_fix
          var total_amt = parseFloat(elem.val())*100000000;
          total_amt = total_amt.toFixed(0);
          if(total_amt<1) total_amt=0;

	    		var pow_amt = Math.abs(total_amt*0.60);
	    		var bnt_amt = Math.abs(total_amt*0.40);
          var rounding_mistake = total_amt - (pow_amt+bnt_amt);
          console.log("Total AMT: " + total_amt + ", Second Total: " + (pow_amt+bnt_amt) + " Rounding mistake: " + rounding_mistake);
          pow_amt += rounding_mistake;

	    		NRS.updateWorkCreationPlots(total_amt,pow_amt,bnt_amt);
	    		$("#fund_pow_cr").html(NRS.formatAmount(pow_amt));
	    		$("#fund_bnt_cr").html(NRS.formatAmount(bnt_amt));
	    		$("#fund_pow2_cr").html(NRS.formatAmount(pow_amt));
	    		$("#fund_bnt2_cr").html(NRS.formatAmount(bnt_amt));
	    	}catch(err) {
	    		NRS.updateWorkCreationPlots(0,0,0);
	    		$("#fund_pow_cr").html(0);
	    		$("#fund_bnt_cr").html(0);
	    		$("#fund_pow2_cr").html(0);
	    		$("#fund_bnt2_cr").html(0);
	    	}
    	}else{
    		NRS.updateWorkCreationPlots(0,0,0);
    		$("#fund_pow_cr").html(0);
	    	$("#fund_bnt_cr").html(0);
	    	$("#fund_pow2_cr").html(0);
	    	$("#fund_bnt2_cr").html(0);
    	}
	}
	});

	NRS.updateWorkCreationPlots = function(totalMoney,powMoney,bountyMoney){

		$("#bal_creation").empty().append(NRS.formatAmount(totalMoney)); // finished

			$("#powfund_creation").sparkline([powMoney,bountyMoney], {
			    type: 'pie',
			    width: '60',
			    height: '60',
			    sliceColors: ['#BCFFB5','#FFD6D6']});

	}

    function updateIncoming(transactions){
    	if (transactions.length) {
				for (var i=0; i<transactions.length; i++) {
					var trans = transactions[i];
					if (trans.confirmed && trans.type == 3 && /* Subtype doesn't matter, we refresh in all cases */ trans.senderRS == NRS.accountRS) {
						NRS.sendRequest("getAccountWork", {
							"account": NRS.account,
							"onlyOneId": trans.transaction,
							"type": 1
						}, function(response) {
							if (!response.work_packages || response.work_packages.length==0) return;
							response.work_packages.forEach(function (s, i, o) {

								if (s) {
									// replace in sidebar
									console.log("Calling REPLACEINSIDEBAR");
									console.log(s);
									replaceInSidebar(s);
									updateWork(s.workId, s);

									// Also update any views that are open
									updateWorkItemView();

								}
							});
						});
					}
					if (!trans.confirmed && trans.type == 3 && trans.subtype == 0 && trans.senderRS == NRS.accountRS) {

						addUnconfirmedWork(trans);
					}
					if (!trans.confirmed && trans.type == 3 && trans.subtype == 1 && trans.senderRS == NRS.accountRS) {
						cancellingUnconfirmed(trans.attachment.id);
					}
				}
			}
    }
	NRS.incoming.myownwork = function(transactions) {
		if (NRS.hasTransactionUpdates(transactions)) {
			updateIncoming(transactions);
		}
	}
	NRS.incoming.workViewNewBlocksHandler = function(transactions) {
		// On new block we always update everything

		// Refetch the list in the sidebar to update the view
		NRS.sendRequest("getAccountWork", {
			"account": NRS.account,
			"type": 1
		}, function(response) {
			if (response.work_packages && response.work_packages.length) {
				for (var i = 0; i < response.work_packages.length; i++) {
					replaceInSidebar(response.work_packages[i]);
					updateWork(response.work_packages[i].workId, response.work_packages[i]);
				}
			}
		});

		// And update the current view
		updateWorkItemView();
	}

	NRS.pages.myownwork = function(callback) {
		_work = [];
		_workToIndex = {};
		$("#no_work_selected").show();
		$("#no_work_confirmed").hide();
		$("#work_details").hide();
		$(".content.content-stretch:visible").width($(".page:visible").width());


		NRS.sendRequest("getAccountWork", {
			"account": NRS.account,
			"type": 1
		}, function(response) {
			if (response.work_packages && response.work_packages.length) {
				for (var i = 0; i < response.work_packages.length; i++) {
					updateWork(response.work_packages[i].workId, response.work_packages[i]);
				}
				displayWorkSidebar(callback);
			} else {
				$("#no_work_selected").show();
				$("#work_details").hide();
				$("#myownwork_sidebar").empty().append(newworkrow);;

			}

			// Also handle unconfirmed TX
			NRS.sendRequest("getUnconfirmedTransactions", {
				"account": NRS.account,
			}, function(response) {
				if (response.unconfirmedTransactions && response.unconfirmedTransactions.length) {
					updateIncoming(response.unconfirmedTransactions);
				}

				// finally do the callback
				NRS.pageLoaded(callback);

			});
		});


	}
	function statusText(message){
		return "<b>" + message.balance_remained + "+</b> XEL left, <b>7742</b> blocks left";
	}
	function status2Text(message){
		return "<b>" + message.percent_done + "%</b> done";
	}
	function ETA(message){

		if(message.cancellation_tx=="0" && message.last_payment_tx=="0")
			return "ETA <b><1.5h</b>";
		else if(message.cancellation_tx!="0" && message.last_payment_tx=="0")
			return "Job Closed";
		else if(message.cancellation_tx=="0" && message.last_payment_tx!="0")
			return "Job Closed";
	}
	function timeOut(message){
		var blocksLeft = parseInt(message.timeout_at_block);
		blocksLeft -= NRS.lastBlockHeight;

		if (blocksLeft > 500)
			blocksLeft=">500";
		if (blocksLeft <=0 )
			blocksLeft="0";


		return "<b>" + blocksLeft + "</b> blocks";
	}
	function writeIfTrue(msg,boolsche){
		if(boolsche)
			return msg;
		else
			return "";
	}
	function efficiency(message){
		return "<b>" + message.bounties_connected + "</b> bounties";
	}
	function statusspan(message){
		if(message.cancellation_tx=="0" && message.last_payment_tx=="0")
			return "<span id='activeLabel' class='label label-success label12px'>Active</span>";
		else if(message.cancellation_tx!="0" && message.last_payment_tx=="0")
			return "<span id='activeLabel' class='label label-danger label12px'>Cancelled</span>";
		else if(message.cancellation_tx=="0" && message.last_payment_tx!="0")
			return "<span id='activeLabel' class='label label-info label12px'>Completed</span>";
	}
	function statusspan_precancel(){
		return "<span id='activeLabel' class='label label-warning label12px'>Cancel Requested</span>";

	}

	function moneyReturned(message){
		return "<b>" + NRS.formatAmount(message.balance_remained) + " XEL</b> refunded";
	}
	function moneyPaid(message){
		return "<b>" + NRS.formatAmount(message.balance_original) + " XEL</b> paid out";
	}

	function balancespan(message){
		return writeIfTrue("<span class='label label-white label12px'>" + NRS.formatAmount(message.balance_remained) + " XEL</span>", message.cancellation_tx=="0" && message.last_payment_tx=="0");
	}

	function flopsFormatter(v, axis) {
        return v.toFixed(axis.tickDecimals) + "G";
    }
    function perHourFormatter(v, axis) {
        return v.toFixed(axis.tickDecimals) + "/h  ";
    }
    function diffFormatter(v, axis) {
        return v.toExponential(1);
    }
	function time_ago(seconds){
		var time_formats = [
		    [60, 'seconds', 1], // 60
		    [120, '1 minute ago', '1 minute from now'], // 60*2
		    [3600, 'minutes', 60], // 60*60, 60
		    [7200, '1 hour ago', '1 hour from now'], // 60*60*2
		    [86400, 'hours', 3600], // 60*60*24, 60*60
		    [172800, 'yesterday', 'tomorrow'], // 60*60*24*2
		    [604800, 'days', 86400], // 60*60*24*7, 60*60*24
		    [1209600, 'last week', 'next week'], // 60*60*24*7*4*2
		    [2419200, 'weeks', 604800], // 60*60*24*7*4, 60*60*24*7
		    [4838400, 'last month', 'next month'], // 60*60*24*7*4*2
		    [29030400, 'months', 2419200], // 60*60*24*7*4*12, 60*60*24*7*4
		    [58060800, 'last year', 'next year'], // 60*60*24*7*4*12*2
		    [2903040000, 'years', 29030400], // 60*60*24*7*4*12*100, 60*60*24*7*4*12
		    [5806080000, 'last century', 'next century'], // 60*60*24*7*4*12*100*2
		    [58060800000, 'centuries', 2903040000] // 60*60*24*7*4*12*100*20, 60*60*24*7*4*12*100
		];
		var token = 'ago', list_choice = 1;

		if (seconds == 0) {
		    return 'just now'
		}
		if (seconds < 0) {
		    seconds = Math.abs(seconds);
		    token = 'from now';
		    list_choice = 2;
		}
		var i = 0, format;
		while (format = time_formats[i++])
		    if (seconds < format[0]) {
		        if (typeof format[2] == 'string')
		            return format[list_choice];
		        else
		            return Math.floor(seconds / format[2]) + ' ' + format[1] + ' ' + token;
		    }
		return "a long time ago";
	}
	function doPlot(){
		try{
			var lmt = 30;
			if(globalType == "5"){
				// 5 min
				lmt = 5*60;
			}
			if(globalType == "15"){
				// 5 min
				lmt = 15*60;
			}
			if(globalType == "60"){
				// 5 min
				lmt = 60*60;
			}
			if(globalType == "1"){
				// 5 min
				lmt = 60*24*60;
			}
			if(globalType == "14"){
				// 5 min
				lmt = 60*24*14*60;
			}
			if(globalType == "30"){
				// 5 min
				lmt = 60*24*30*60;
			}
			/*if(computation_power.length == 0){
				$("#flot_alert").removeClass("flot_hidden");
			}else{
				$("#flot_alert").addClass("flot_hidden");
			}*/
			$("#flot_alert").addClass("flot_hidden");
			var compu_array_normed = [];
			var compu_array_normed_diff = [];
			var last = 0;
			var lastDate = null;
			var t = null;
			var lasttt = 0;

			// Add first limit date
			var firstDateP = new Date();
			var firstDatePL = new Date(firstDateP.getTime() - 1000*lmt);
			lastDate=firstDatePL;
			compu_array_normed.push([firstDatePL, 0]);
			for (x in computation_power){
				var ttstr = computation_power[x][2];
			    while (ttstr.length < 32) ttstr = "0" + ttstr;
			    ttstr = ttstr.substring(0,16);
				var date = new Date(parseInt(computation_power[x][1])*1000);
				if (date == null) continue;
				if(t==null){
					var successorLD = new Date(date.getTime() - 1000); // + 1 day in ms
					t=successorLD;
					compu_array_normed.push([successorLD,0]);		
					compu_array_normed_diff.push([firstDatePL, parseInt(ttstr,16)]);
				}
				lastDate=date;

				console.log(computation_power[x]);
				// here normalize values by difficulty (TODO FIXME)
				last = computation_power[x][0];
				compu_array_normed.push([date, computation_power[x][0]]);
				lasttt = parseInt(ttstr, 16);
				

				compu_array_normed_diff.push([date,lasttt]);

			}
			if(lastDate == null) return;
			var date = new Date();
			var followingLD = new Date(lastDate.getTime() + 1000); // + 1 day in ms

			compu_array_normed.push([followingLD, 0]);
			compu_array_normed.push([date, 0]);
			compu_array_normed_diff.push([date, lasttt]);

			//compu_array_normed_diff.push([date, 0]);

			tff="%H:%M";
		    if(globalType=="14"||globalType=="30"){
		    	tff="%d/%m";	
		    }
			$.plot($("#flot-line-chart-multi"), [compu_array_normed], {
		        xaxes: [{
		            mode: 'time',timeformat: tff, min: firstDatePL, max: date,tickInterval:'1 minute'
		        }],
		        grid: {borderColor: 'transparent', shadow: false, drawBorder: false, shadowColor: 'transparent'},
		        series: {
			        lines: { show: true, fill: true, fillColor: "#FCB1B8" },
			        points: { show: true, fill: false }
			    }, colors: ["#FCB1B8", "#FCB1B8"],
		        yaxes: [{
		            min: 0, tickFormatter: perHourFormatter
		        }, {
		            // align if we are to the right
		            alignTicksWithAxis: 1,
		            position: "right",
		            tickFormatter: flopsFormatter
		        }],
		        legend: {
		            position: 'ne'
		        }
		    });
		    $.plot($("#flot-line-chart-multi_2"), [compu_array_normed_diff], {
		        xaxes: [{
		            mode: 'time',timeformat: tff, min: firstDatePL, max: date,tickInterval:'1 minute'
		        }],
		        grid: {borderColor: 'transparent', shadow: false, drawBorder: false, shadowColor: 'transparent'},
		        series: {
			        lines: { show: true, fill: true, fillColor: "#b3e6ff" },
			        points: { show: true, fill: false }
			    }, colors: ["#b3e6ff", "#b3e6ff"],
		        yaxes: [{
		            min: 0, tickFormatter: diffFormatter
		        }, {
		            // align if we are to the right
		            alignTicksWithAxis: 1,
		            position: "right",
		            tickFormatter: flopsFormatter
		        }],
		        legend: {
		            position: 'ne'
		        }
		    });
		    console.log("Plot has been drawn!");
		    
		} catch (e) {
			console.log("plot failed, e = " + e);
			console.log(e.stack);
		}

	}



	function bottom_status_row(message){
		if(message.cancellation_tx=="0" && message.last_payment_tx=="0"){
			return "<div class='row fourtwenty'><div class='col-md-3'><i class='fa fa-tasks fa-fw'></i> " + status2Text(message) + "</div><div class='col-md-3'><i class='fa fa-hourglass-1 fa-fw'></i> " + ETA(message) + "</div><div class='col-md-3'><i class='fa fa-times-circle-o fa-fw'></i> " + timeOut(message) + "</div><div class='col-md-3'><i class='fa fa-star-half-empty fa-fw'></i> " + efficiency(message) + "</div></div>";
		}
		else if(message.cancellation_tx!="0" && message.last_payment_tx=="0"){
			return "<div class='row fourtwenty'><div class='col-md-3'><i class='fa fa-hourglass-1 fa-fw'></i> " + ETA(message) + "</div><div class='col-md-6'><i class='fa fa-mail-reply fa-fw'></i> " + moneyReturned(message) + "</div><div class='col-md-3'><i class='fa fa-star-half-empty fa-fw'></i> " + efficiency(message) + "</div></div>";
		}
		else if(message.cancellation_tx=="0" && message.last_payment_tx!="0"){
			return "<div class='row fourtwenty'><div class='col-md-3'><i class='fa fa-hourglass-1 fa-fw'></i> " + ETA(message) + "</div><div class='col-md-6'><i class='fa fa-mail-forward fa-fw'></i> " + moneyPaid(message) + "</div><div class='col-md-3'><i class='fa fa-star-half-empty fa-fw'></i> " + efficiency(message) + "</div></div>";
		}
	}

	function blockToAgo(blockHeight){
		var span = NRS.lastBlockHeight - blockHeight;
		var minPerBlock = 1;
		var secondsPassed = minPerBlock*span*60;
		return time_ago(secondsPassed);
	}

	function updateWork(workId, workPackage){
		if(_workToIndex[workId]==null){
			_work.push(workPackage);
			for (var i = 0; i < _work.length; i++) {
					_workToIndex[_work[i].workId] = i;
			}
		}else{
			_work[_workToIndex[workId]]=workPackage;
		}
	}

	function replaceInSidebar(message){
		newElement = "<a href='#' data-workid='" + message.workId + "' class='list-group-item larger-sidebar-element selectable'><p class='list-group-item-text agopullright'>" + balancespan(message) + " " + statusspan(message) + " <span class='label label-primary label12px'>" + message.language + "</span></p><span class='list-group-item-heading betterh4'>" + message.title + "</span><br><small>created " + blockToAgo(message.block_height_created) + " (block #" + message.block_height_created + ")</small><span class='middletext_list'>" + /* BEGIN GRID */ bottom_status_row(message) /* END GRID */ + "</span></span></a>";
		if($("#myownwork_sidebar").children().filter('[data-workid="' + message.workId + '"]').length>0){
			var hasActiveClass=$("#myownwork_sidebar").children().filter('[data-workid="' + message.workId + '"]').hasClass("active");
			$("#myownwork_sidebar").children().filter('[data-workid="' + message.workId + '"]').replaceWith(newElement);
			if(hasActiveClass){
				$("#myownwork_sidebar").children().filter('[data-workid="' + message.workId + '"]').addClass("active");
			}
		}else{
			console.log("ADDING");
			console.log(message);
			$(".grayadder").after(newElement);
		}
	}

	function addUnconfirmedWork(transactionObj){
		if($("#myownwork_sidebar").children().filter('[data-workid="' + transactionObj.transaction + '"]').length==0){
			newElement = "<a href='#' data-workid='" + transactionObj.transaction + "' class='list-group-item larger-sidebar-element selectable' ><p class='list-group-item-text agopullright'><span class='label label-danger label12px'>Unconfirmed Work</span></p><span class='list-group-item-heading betterh4'>" + transactionObj.attachment.title + "</span><br><div class='laterdiv'>Details will become visible after the first confirmation. Please hang tight!</div></a>";
			$(".grayadder").after(newElement);
		}

	}

	function cancellingUnconfirmed(workId){
		$("#myownwork_sidebar").children().filter('[data-workid="' + workId + '"]').find("#activeLabel").replaceWith(statusspan_precancel());
	}

	function displayWorkSidebar(callback) {
		console.log("mywork callback fired!");
		var activeAccount = false;

		var $active = $("#myownwork_sidebar a.active");

		if ($active.length) {
			activeAccount = $active.data("account");
		}

		var rows = "";
		var menu = "";

		// Here, add the NEW WORK row
		rows += newworkrow;
		for (var i = 0; i < _work.length; i++) {
			var message = _work[i];
			rows += "<a href='#' data-workid='" + message.workId + "' class='list-group-item larger-sidebar-element selectable'><p class='list-group-item-text agopullright'>" + balancespan(message) + " " + statusspan(message) + " <span class='label label-primary label12px'>" + message.language + "</span></p><span class='list-group-item-heading betterh4'>" + message.title + "</span><br><small>created " + blockToAgo(message.block_height_created) + " (block #" + message.block_height_created + ")</small><span class='middletext_list'>" + /* BEGIN GRID */ bottom_status_row(message) /* END GRID */ + "</span></span></a>";
		}

		$("#myownwork_sidebar").empty().append(rows);

		if (activeAccount) {
			$("#myownwork_sidebar a[data-account=" + activeAccount + "]").addClass("active").trigger("click");
		}

		NRS.pageLoaded(callback);
	}

	$("#cancel_btn").click(function(e) {
		e.preventDefault();

		if (NRS.downloadingBlockchain) {
			$.growl($.t("error_forging_blockchain_downloading"), {
				"type": "danger"
			});
		} else if (NRS.state.isScanning) {
			$.growl($.t("error_forging_blockchain_rescanning"), {
				"type": "danger"
			});
		} else{
			$("#cancel_work_modal").modal("show");
		}
	});
	function doplttype(type,textY,refresh) {

		console.log("Selected Type: " + type);
		$("#dpdwn_type li a").parents(".btn-group").find(".text").text(textY);
		$(".popover").remove();

		// REPLOT
		// SAVE
		globalType = type;
		localStorage.setItem('plotscale', type);
		localStorage.setItem('plotscaleTXT', textY);

		if(refresh){
			// Now load real data
			var lmt = 30;
			if(globalType == "5"){
				// 5 min
				lmt = 5;
			}
			if(globalType == "15"){
				// 5 min
				lmt = 15;
			}
			if(globalType == "60"){
				// 5 min
				lmt = 60;
			}
			if(globalType == "1"){
				// 5 min
				lmt = 60*24;
			}
			if(globalType == "14"){
				// 5 min
				lmt = 60*24*14;
			}
			if(globalType == "30"){
				// 5 min
				lmt = 60*24*30;
			}
			// Now load real data
			NRS.sendRequest("getAccountWorkEfficiencyPlot", {
			"workId": globalWorkItem,
			"last_num": lmt
			}, function(response) {
				if (response.computation_power) {
					computation_power = response.computation_power;
					doPlot(); // refresh
				}else{
					computation_power = [];
					doPlot(); // refresh
				}
			});
		}
	}
	$("#dpdwn_type li a").click(function(e) {
		e.preventDefault();
		var type = $(this).data("type");
		var textY = $(this).text();
		doplttype(type,textY, true);
	});
	$("#myownwork_sidebar").on("click", "a", function(e) {
		var arrayIndex = $(this).data("workid");
		var realIndex = _workToIndex[arrayIndex];
		if(realIndex!=null){
		  workItem = _work[realIndex];
		}
		// restore plot scale
		var p01 = localStorage.getItem('plotscale');
		var p02 = localStorage.getItem('plotscaleTXT');
		if(p01 != null && p02 != null){
			doplttype(p01,p02,false);
		}
		if(workItem == null){
			$("#no_work_selected").hide();
			$("#no_work_confirmed").show();
			$("#work_details").hide();
			$("#myownwork_sidebar a.active").removeClass("active");
			if($(this).hasClass("selectable")){
				e.preventDefault();
				$(this).addClass("active");
			}
			return;
		}

		console.log("Updating view for: " + workItem);
		computation_power=[];
		solution_rate=[];

		

		$("#myownwork_sidebar a.active").removeClass("active");
		if($(this).hasClass("selectable")){
			e.preventDefault();
			$("#myownwork_sidebar a.active").removeClass("active");
			$(this).addClass("active");
			
			

			
			updateWorkItemView();



			$("#no_work_selected").hide();
			$("#no_work_confirmed").hide();
			$("#work_details").show();
		}else{
			$("#no_work_selected").show();
			$("#no_work_confirmed").hide();
			$("#work_details").hide();
		}
	});
	var updateWorkItemView = function(){

		if(workItem==null){
			return;
		}
		
		// TODO, create labels
		$("#cancel_btn").hide();
		if(workItem.cancellation_tx=="0" && workItem.last_payment_tx=="0"){
			$("#work_indicator").removeClass("btn-success").removeClass("btn-warning").removeClass("btn-default").removeClass("btn-info").addClass("btn-success");
			$("#work_indicator_inner").empty().append("Active");
			$("#cancel_btn").show();
			if ($("#myownwork_sidebar").children().filter('[data-workid="' + workItem.workId + '"]').find(".label-warning").length>0){
				$("#work_indicator").removeClass("btn-warning").removeClass("btn-success").removeClass("btn-default").removeClass("btn-info").addClass("btn-warning");
				$("#work_indicator_inner").empty().append("Cancel Requested");
				$("#cancel_btn").hide();
			}

			$("#hideable").show();
			$("#balancelefttitle").empty().append("Balance Left");
			$("#detailedlisting").empty().append("[<a href=#'>breakdown</a>]");
		}
		else if(workItem.cancellation_tx!="0" && workItem.last_payment_tx=="0"){
			$("#work_indicator").removeClass("btn-warning").removeClass("btn-success").removeClass("btn-default").removeClass("btn-info").addClass("btn-default");
			$("#work_indicator_inner").empty().append("Cancelled");
			$("#hideable").show();
			$("#balancelefttitle").empty().append("Balance Refunded");
			$("#detailedlisting").empty().append("[<a href=# data-transaction='" + workItem.cancellation_tx + "'>payback TX</a>, <a href=#'>breakdown</a>]");
		}
		else if(workItem.cancellation_tx=="0" && workItem.last_payment_tx!="0"){
			$("#work_indicator").removeClass("btn-warning").removeClass("btn-success").removeClass("btn-default").removeClass("btn-info").addClass("btn-info");
			$("#work_indicator_inner").empty().append("Finished");
			$("#hideable").hide();
			$("#balancelefttitle").empty().append("...");
			$("#detailedlisting").empty().append("");
		}

		$("#job_id").empty().append(workItem.workId);
			document.getElementById("workId").value = workItem.workId;

			// Now fill the right side correctly
			$("#work_title_right").empty().append(workItem.title);

			// Percentages
			$("#bal_work").empty().append(workItem.percent_work*100);
			$("#bal_bounties").empty().append(workItem.percent_bounties*100);


			$("#bal_original").empty().append(NRS.formatAmount(workItem.balance_original));
			$("#bal_remained").empty().append(NRS.formatAmount(workItem.balance_remained));
			$("#bnt_connected").empty().append(workItem.bounties_connected);

			var percentRemained = Math.round(workItem.balance_remained * 100 / workItem.balance_original);
			$("#bal_remained_percent").empty().append(percentRemained); // left

			var origBntFund = workItem.balance_original_bounty;




			var bountyGone = false;
			if(parseInt(workItem.bounties_connected)>0){
				$("#bountyfundthere").hide();
				$("#bountyfundgone").show();
				$("#bnt_percent_left").empty().append("0");
				$("#bal_remained_bnt").empty().append(NRS.formatAmount(0));
				bountyGone = true;
			}else{
				$("#bountyfundthere").show();
				$("#bountyfundgone").hide();
				$("#bnt_percent_left").empty().append("100");
				$("#bal_remained_bnt").empty().append(NRS.formatAmount(origBntFund));
			}

			$("#refund_calculator").empty().append(NRS.formatAmount(workItem.balance_remained));

			var bountiesLimit = parseInt(workItem.bounty_limit);
			var bountiesMissing = bountiesLimit - parseInt(workItem.bounties_connected);

			var gotNumberPow = parseInt(workItem.pow_connected);
			$("#number_pow").empty().append(gotNumberPow);
      		console.log("Activated:");
      		console.log(workItem);
			var bal_original_pow = workItem.balance_original_pow; // fix here
			var bal_left_pow = bal_original_pow - workItem.paid_pow; // fix here
			console.log("BAL ORIGINAL POW: " + bal_original_pow);
			var bal_pow_perc_left = Math.round(bal_left_pow*100 / bal_original_pow);
			$("#pow_paid_out").empty().append(NRS.formatAmount(bal_original_pow-bal_left_pow));
			$("#bal_remained_pow").empty().append(NRS.formatAmount(bal_left_pow));
			$("#bal_remained_pow_percent").empty().append(bal_pow_perc_left); // finished
			$("#bal_remained_pow_percent_2").empty().append(100-bal_pow_perc_left); // finished

			$("#progbar_pow").attr("aria-valuenow",parseInt(100-bal_pow_perc_left));
			$("#progbar_pow").css("width",parseInt(100-bal_pow_perc_left) + "%");

			$("#powfund").sparkline([bal_left_pow,bal_original_pow-bal_left_pow], {
			    type: 'pie',
			    width: '48',
			    height: '48',
			    sliceColors: ['#BCFFB5','#FFD6D6']});

			if(bountyGone)
				$("#bountyfund").sparkline([0,100], {
				    type: 'pie',
				    width: '48',
				    height: '48',
				    sliceColors: ['#BCFFB5','#FFD6D6']});
			else
				$("#bountyfund").sparkline([100,0], {
				    type: 'pie',
				    width: '48',
				    height: '48',
				    sliceColors: ['#BCFFB5','#FFD6D6']});

			$("#refundfund").sparkline([workItem.balance_remained,workItem.balance_original-workItem.balance_remained], {
			    type: 'pie',
			    width: '48',
			    height: '48',
			    sliceColors: ['#BCFFB5','#FFD6D6']});

			if(workItem.language=="ElasticPL")
				$("#programming_language").empty().append("Elastic Programming Language v1");

			$("#blockchain_bytes").empty().append(formatBytes(parseInt(workItem.script_size_bytes)));
			$("#fee").empty().append(NRS.formatAmount(workItem.fee));

			$("#num_in").empty().append(workItem.num_input);
			$("#percent_done").empty().append(workItem.percent_done);
			$("#progbar_work").attr("aria-valuenow",parseInt(workItem.percent_done));
			$("#progbar_work").css("width",workItem.percent_done + "%");

			
			// plot with loading indicator
			doPlot();
			globalWorkItem = workItem.workId;
			// Estimate number of blocks to fetch
			var lmt = 30;
			if(globalType == "5"){
				// 5 min
				lmt = 5;
			}
			if(globalType == "15"){
				// 5 min
				lmt = 15;
			}
			if(globalType == "60"){
				// 5 min
				lmt = 60;
			}
			if(globalType == "1"){
				// 5 min
				lmt = 60*24;
			}
			if(globalType == "14"){
				// 5 min
				lmt = 60*24*14;
			}
			if(globalType == "30"){
				// 5 min
				lmt = 60*24*30;
			}
			// Now load real data
			NRS.sendRequest("getAccountWorkEfficiencyPlot", {
			"workId": workItem.workId,
			"last_num": lmt
			}, function(response) {
				if (response.computation_power) {
					computation_power = response.computation_power;
					doPlot(); // refresh
				}else{
					computation_power = [];
					doPlot(); // refresh
				}
			});
}
	return NRS;
}(NRS || {}, jQuery));
