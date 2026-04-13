var ROOT_URL = "."
//var ROOT_URL = "http://localhost:8080"

// w : Pedantic Warnings
// p : Different Parts         (RDKit)
// e : Ignore Element Changes  (Daylight)
// h : Ignore Impl H Changes   (RDKit,Daylight)
// H : Ignore Total H Changes  (Daylight)
// 0 : Ignore H0               (RDKit)
// o : Overwrite Bond          (RDKit)
// m : Remove Unmapped         (RDKit)
// r : Recalc Hydrogens        (RDKit)
// X : Expand Hydrogens        (Daylight)

const RD_PRESET = 'ph0omr';
const DY_PRESET = 'ehHx';

let modified = false;
let last_op = null;

function set_rd_checkboxes()
{
	$('table.smirks-opts input[name=p]').prop('checked', true);
	$('table.smirks-opts input[name=e]').prop('checked', false);
	$('table.smirks-opts input[name=h]').prop('checked', true);
	$('table.smirks-opts input[name=H]').prop('checked', false);
	$('table.smirks-opts input[name=0]').prop('checked', true);
	$('table.smirks-opts input[name=o]').prop('checked', true);
	$('table.smirks-opts input[name=m]').prop('checked', true);
	$('table.smirks-opts input[name=r]').prop('checked', true);
	$('table.smirks-opts input[name=x]').prop('checked', false);
	update();
}

function set_dy_checkboxes()
{
	$('table.smirks-opts input[name=p]').prop('checked', false);
	$('table.smirks-opts input[name=e]').prop('checked', true);
	$('table.smirks-opts input[name=h]').prop('checked', true);
	$('table.smirks-opts input[name=H]').prop('checked', true);
	$('table.smirks-opts input[name=0]').prop('checked', false);
	$('table.smirks-opts input[name=o]').prop('checked', false);
	$('table.smirks-opts input[name=m]').prop('checked', false);
	$('table.smirks-opts input[name=r]').prop('checked', false);
	$('table.smirks-opts input[name=x]').prop('checked', true);
	update();
}

function set_smirks_opts()
{
	let choice = $('select[name=preset] option:selected').val();
	if (choice == 'rd')
	  set_rd_checkboxes();
	else if (choice == 'dy')
      set_dy_checkboxes();
}

function set_smirks_preset() {
	let opt_str = $('table.smirks-opts input').get().filter(e => e.checked).map(e => e.name).join('');
	if (opt_str === DY_PRESET) {
	   $('select[name=preset] option[value=dy]').prop('selected', true)
	} else if (opt_str === RD_PRESET) {
       $('select[name=preset] option[value=rd]').prop('selected', true)
    } else {
       $('select[name=preset] option[value=x]').prop('selected', true)
    }
    update();
}

function set_mode(mode)
{
	$('#react').removeClass('mode-react')
	           .removeClass('mode-norm')
	           .removeClass('mode-map')
	           .addClass('mode-' + mode);
	$('#input-right').prop('readonly', mode != 'react');

	if (mode == 'react') {
	  $('#smirks').val('[#6:1][Cl,Br,F,I].[#6:2]B(O)(O)>>[*:1]-[*:2]');
      $('#input-left').val(
    	'B(c1cccc(c1)n2c(cc(n2)C)C)(O)O.CC(C)(C)OC(=O)CBr bromo coupling\n' +
    	'B(c1ccncc1)(O)O.CN(C)c1cncc(n1)Cl chloro coupling\n' +
    	'B(c1ccncc1)(OC(C)(C)C1(C)C)O1.CN(C)c1cncc(n1)Cl try with different semantics\n');
      $('#input-right').val('');
      $('select[name=abbr] option[value=on]').prop('selected', true);
      $('select[name=annotate] option[value=none]').prop('selected', true);
      $('select[name=mode] option[value=once]').prop('selected', true);
	} else if (mode == 'norm') {
	  if (!modified) {
	    $('#smirks').val('[N:1](=[O:2])=[O:3]>>[N+:1](=[O:2])[O-:3]');
      	$('#input-left').val(
      	'Cc1cc(N(=O)=O)ccc1 single nitro-group\n'+
      	'Cc1c(N(=O)=O)cc(N(=O)=O)cc1N(=O)=O multiple nitro-groups\n'+
      	'c1cc([N+](=O)[O-])ccc1 no change needed\n' +
      	'CC(=O)c1ccc(cc1)N.c1cncc(c1Cl)[N+](=O)=O.Cl>CCO>CC(=O)c1ccc(cc1)Nc2ccncc2[N+](=O)[O-].Cl |f:1.2,4.5| reaction\n'
      	);
      	$('#input-right').val('');
      	$('select[name=abbr] option[value=off]').prop('selected', true);
      	$('select[name=annotate] option[value=none]').prop('selected', true);
      }
      $('select[name=mode] option[value=exclusive]').prop('selected', true);
	} else if (mode == 'map') {
	  if (!modified) {
	    $('#smirks').val('[#6:1][Cl,Br,F,I].[#6:2]B(O)(O)>>[*:1][*:2]');
	    $('#input-left').val('B(c1cccc(c1)n2c(cc(n2)C)C)(O)O.CC(C)(C)OC(=O)CBr>>Cc1cc(n(n1)c2cccc(c2)CC(=O)OC(C)(C)C)C\n' +
	'B(c1ccncc1)(O)O.CN(C)c1cncc(n1)Cl>>CN(C)c1cncc(n1)c2ccncc2\n');
	    $('#input-right').val('');
	    $('select[name=abbr] option[value=on]').prop('selected', true);
	    $('select[name=annotate] option[value=colmap]').prop('selected', true);
	  }
	  $('select[name=mode] option[value=once]').prop('selected', true);
	}
}

function clearInput() {
  $('#smirks').val('');
  $('#input-left').val('');
  $('#input-right').val('');
}

function toggleExtraOpts() {
  $('#depict_extra_opts').slideToggle();
}

function smittl(smiles) {
  var i = smiles.indexOf(' ');
  var j = smiles.indexOf('\t');
  if (j >= 0 && (j < i || i < 0))
    i = j;
  if (i < 0)
    return ''; // no title
  return smiles.substring(i+1);
}

function strip(smiles, direction) {
	let parts = smiles.split('>', 3);
	if (direction == 'backward') {
		return parts[0];
	} else if (direction == 'forward') {
		// strip any CXSMILES layers from the title
		return parts[2] ? parts[2].replace(/[ \t][|][^|]+[|]/, "") : parts[2];
	}
	return smiles;
}

async function react_smiles(dir, opts) {

  var result = [];

  let $input_box = dir !== 'backward' ? $('#input-left') : $('#input-right');
  let $output_box = dir !== 'backward' ? $('#input-right') : $('#input-left');

  let inputs = $input_box.val().split("\n");
  inputs = inputs.map(e => e.trim()).filter(e => e);

  var nlines = inputs.length < 500 ? inputs.length : 500;
  if (inputs.length > 500) {
    alert("Only the first 500 entries will be displayed!");
  }

  let opt_str = $('table.smirks-opts input').get().filter(e => e.checked).map(e => e.name).join('');
  if (opt_str === DY_PRESET) {
    opt_str = 'Daylight';
  } else if (opt_str === RD_PRESET) {
	opt_str = 'RDKit';
  }

  var params = [['r', $('input[name="smirks"]').val()],
                ['o', opt_str]];
  params.push(... inputs.map(function(e){return ['s', e]}));

  const mode = $('select[name="mode"]').val();
  let url = ROOT_URL + '/react/' + dir;
  if (mode)
  	url += '/' + mode;

  var results = await fetch(url,
        {
         	method: "POST",
         	body: new URLSearchParams(params)
        }).then((response) => {
			if (!response.ok) {
				throw new Error(`HTTP error, status = ${response.status}`);
		  	}
		  	return response.text();
 		}).then((text) => {
 			var results = text.split('\n');

 			$output_box.val(results.map(e => strip(e, dir)).join('\n'));

 			var boxes = [];
 			for (var i=0; i<results.length; i++) {
 				var smiles = results[i];
 				if (smiles.length == 0 || smiles.charAt(0) === '#')
                  continue; // todo push blank
                var title = smittl(smiles);
                title = title.replace(/^\|[^|]+\|\s+/, "");
                if (!title)
                	title = "#" + (1+i);
                boxes.push(generate(opts, smiles, title));
 			}
 			return boxes;
 		});
 	return results;
}

async function run(dir) {
	last_op = dir;
	update();
}

async function update() {
  if (last_op) {
	  var opts = {
				  'style':     $("select[name='style'] option:selected").val(),
				  'annotate':  $("select[name='annotate'] option:selected").val(),
				  'hdisp':     $("select[name='hdisp'] option:selected").val(),
				  'showtitle': $("input[name='showtitle']").is(':checked'),
				  'abbr':      $("select[name='abbr'] option:selected").val()};
	  $('#result').removeClass().addClass(opts.style);
	  $('#result').html(await react_smiles(last_op, opts));
  }
}

function depict_url(opts, smiles, fmt, w, h) {
	var smi = encodeURIComponent(smiles);
	var url = ROOT_URL + '/depict/' + opts.style + '/' + fmt + '?smi=' + smi;
	if (w && h)
	  url += '&w=' + w + '&h=' + h;
	url += '&abbr=' + opts.abbr;
	url += '&hdisp=' + opts.hdisp;
    if (opts.annotate && opts.annotate !== 'none')
     url += '&annotate=' + encodeURIComponent(opts.annotate);
	return url;    
}

function generate(opts, smiles, title) {
    var isrxn    = smiles.indexOf('>') != -1;
    var numparts = isrxn ? ((smiles.split('>').length - 1) / 2) : 1;

    var classType = 'molecule';
    if (isrxn) {
        if (numparts > 1)
            classType = 'scheme';
        else
            classType = 'reaction';
    }

    var svg_url = depict_url(opts, smiles, 'svg', -1, -1);
    var png_url = depict_url(opts, smiles, 'png', -1, -1);
    var pdf_url = depict_url(opts, smiles, 'pdf', -1, -1);

    var $outer = $('<div>').addClass('chemdiv')
                           .addClass(classType);
    var $img = $('<div class="img">').append('<span class="valign-helper"></span>')
                                     .append($('<a href="' + svg_url + '">').append($('<img onerror="handle_img_error(this)">').addClass('chemimg').attr('src', svg_url)));
    var $div =  $('<div class="grid">').append($img);
    var $links = $("<div class='links'>");
    $links.append('<a title="Download SVG" href="' + svg_url + '" download="' + title + '.svg"><i class="icon-file-svg icon" aria-hidden="true"></i></a><br>');
    $links.append('<a title="Download PNG" href="' + png_url + '" download="' + title + '.png"><i class="icon-file-png icon" aria-hidden="true"></i></a><br>');
    $links.append('<a title="Download PDF" href="' + pdf_url + '" download="' + title + '.pdf"><i class="icon-file-pdf icon" aria-hidden="true"></i></a>');
    $img.append($links);
    if (!opts.showtitle)
      $div.append($('<div class="title">').append(title));
    $div.append($('<div class="smiles">').append(smiles));
    $outer.append($div);
    return $outer;
}

function handle_img_error(img) {
  $.ajax($(img).attr('src')).fail(function(r){
    reason = r.responseText;
    var tempDom = $('<output>').append($.parseHTML(reason));
    console.log($('div', tempDom).html());
    $(img).parent().parent().html($('<div class="error-mesg">').append($('div', tempDom).html()));
    $(img).parent().parent().addClass("error");
  });
}

function load_forward_demo() {
	$('#help-text').css('display', 'block');
	$('#help-text').html(
	'<b>Forward (<img style="vertical-align: middle" src="img/forward.svg" width="20">)</b>: Running a SMIRKS forward applies the transform to a ' +
	'set of reactants specified in the left text box, if the transform could be applied ' +
	'a reaction is produced and the product are set in the right text box. Example SMIRKS and reactants that performs a suzuki coupling has been ' +
	'loaded for you, click the <img style="vertical-align: middle" src="img/forward.svg" width="20"> button between the boxes to run the reaction ' +
	'and see the results.<br/><br/>' +
	'By default only the first successful application of a transform is shown, if there are multiple sites the reaction can occur you can view all of these ' +
	'by selecting this from the second option box below.');
	$('#smirks').val('[#6:1][Cl,Br,F,I].[#6:2]B(O)(O)>>[*:1][*:2]');
	$('#input-left').val(
	'B(c1cccc(c1)n2c(cc(n2)C)C)(O)O.CC(C)(C)OC(=O)CBr\n' +
	'B(c1ccncc1)(O)O.CN(C)c1cncc(n1)Cl\n' +
	'B(c1ccncc1)(OC(C)(C)C1(C)C)O1.CN(C)c1cncc(n1)Cl\n');
	$('#input-right').val('');
}

function load_backward_demo() {
	$('#help-text').css('display', 'block');
	$('#help-text').html('<b>Backward (<img style="vertical-align: middle" src="img/backward.svg" width="20">)</b>: Running a SMIRKS backward applies the transform to a ' +
                         	'set of product specified in the right text box, if the transform could be applied ' +
                         	'a reaction is produced and the reactant set is added in the left text box. Example SMIRKS and reactants that performs a suzuki (un)coupling has been ' +
                         	'loaded for you, click the <img style="vertical-align: middle" src="img/backward.svg" width="20"> button between the boxes to run the reaction ' +
                         	'and see the results.<br/><br/>' +
                         	'Note that a SMIRKS written to be run forwards may not work correctly backwards, particular care must be taken with query features which should only appear on the right hand side.' +
                         	'<br/><br/>' +
                         	'By default only the first successful application of a transform is shown, if there are multiple sites the reaction can occur you can view all of these ' +
                         	'by selecting this from the second option box below.');
	$('#smirks').val('[*:1][Cl].[*:2]B(OC(C)(C)C1(C)C)(O1)>>[#6:1]!@[#6:2]');
	$('#input-right').val(
	'c1(cccc(c1)-n2c(cc(n2)C)C)CC(OC(C)(C)C)=O\n' +
	'c1(ccncc1)-c2cncc(N(C)C)n2\n');
	$('#input-left').val('');
}

function load_map_demo() {
	$('#help-text').css('display', 'block');
	$('#help-text').html('Example: When using SMIRKS to map a reaction we provide a reaction as input in the left hand box.');
	$('#smirks').val('[#6:1][Cl,Br,F,I].[#6:2]B(O)(O)>>[*:1][*:2]');
	$('#input-left').val('B(c1cccc(c1)n2c(cc(n2)C)C)(O)O.CC(C)(C)OC(=O)CBr>>Cc1cc(n(n1)c2cccc(c2)CC(=O)OC(C)(C)C)C\n' +
	'B(c1ccncc1)(O)O.CN(C)c1cncc(n1)Cl>>CN(C)c1cncc(n1)c2ccncc2\n');
	$('#input-right').val('');
}

function load_normalize_demo() {
	$('#help-text').css('display', 'block');
	$('#help-text').html('<b>Normalize (<img style="vertical-align: middle" src="img/normalize.svg" width="20">)</b>: Running a SMIRKS backward applies the transform to a ' +
                                                  	'set of product specified in the right text box');
	$('#smirks').val('[N:1](=[O:2])=[O:3]>>[N+:1](=[O:2])[O-:3]');
	$('#input-left').val(
	'Cc1cc(N(=O)=O)ccc1 single nitro-group\n'+
	'Cc1c(N(=O)=O)cc(N(=O)=O)cc1N(=O)=O multiple nitro-groups\n'+
	'c1cc([N+](=O)[O-])ccc1 no change needed\n' +
	'CC(=O)c1ccc(cc1)N.c1cncc(c1Cl)[N+](=O)=O.Cl>CCO>CC(=O)c1ccc(cc1)Nc2ccncc2[N+](=O)[O-].Cl |f:1.2,4.5| reaction\n'
	);
	$('#input-right').val('');
}