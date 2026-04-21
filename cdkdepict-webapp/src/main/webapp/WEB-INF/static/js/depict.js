var ROOT_URL = "."
//var ROOT_URL = "http://localhost:8080/cdkdepict/"

function clearInput() {
  $('#input').val('');
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

function renderSMILES(inputs, opts) {
  var result = [];

  var nlines = inputs.length < 500 ? inputs.length : 500;
  if (inputs.length > 500) {
    alert("Only the first 500 entries will be displayed!");
  }

  for (var i = 0; i < nlines; i++) {
    var input = inputs[i].trim();

    // skip empty lines and comments
    if (input.length == 0 || input.charAt(0) === '#')
      continue

    var title = smittl(input);
    title = title.replace(/^\|[^|]+\|\s+/, "");
    if (!title)
      title = "#" + (1+i);

    result.push(generate(opts, input, title));
  }
  return result;
}

function renderCTAB(inputs, opts) {
  var result = [];
  var nlines = inputs.length < 500 ? inputs.length : 500;
  if (inputs.length > 500) {
    alert("Only the first 500 entries will be displayed!");
  }

  for (var i = 0; i < nlines; i++) {
    var input = inputs[i];

    // skip empty lines and comments
    if (input.trim().length == 0)
      continue

    result.push(generate(opts, input, input.substring(0, input.indexOf('\n'))));
  }
  return result;
}

function update() {
  var input  = $('#input').val();
  var result = $('#result');
  result.empty();

  var opts = {
              'style':     $("select[name='style'] option:selected").val(),
              'annotate':  $("select[name='annotate'] option:selected").val(),
              'zoom':      $("input[name='zoom']").val(),
              'flip':      $("input[name='flip']").is(':checked'),
              'rotate':    $("input[name='rotate']").val(),
              'sma':       $("input[name='smarts']").val(),
              'hdisp':     $("select[name='hdisp'] option:selected").val(),
              'showtitle': $("input[name='showtitle']").is(':checked'),
              'abbr':      $("select[name='abbr'] option:selected").val(),
              'arw':       $("select[name='arw'] option:selected").val(),
              'dat':       $("select[name='dat'] option:selected").val()
              };

  result.removeClass().addClass(opts.style);

  var inputs = [];

  if (input.indexOf("V2000") >= 0 && input.indexOf("M  END") >= 0) {
    inputs = input.split("$$$$\n");
    result.append(renderCTAB(inputs, opts));
  } else if (input.indexOf("V3000") >= 0 && input.indexOf("M  END") >= 0) {
    inputs = input.split("$$$$\n");
    result.append(renderCTAB(inputs, opts));
  } else {
    inputs = input.split("\n");
    result.append(renderSMILES(inputs, opts));
  }
}

function depict_url(opts, smiles, fmt, w, h) {
	var smi = encodeURIComponent(smiles);
	var url = ROOT_URL + '/depict/' + opts.style + '/' + fmt + '?smi=' + smi;
	if (w && h)
	  url += '&w=' + w + '&h=' + h;
	url += '&abbr=' + opts.abbr;
	url += '&hdisp=' + opts.hdisp;
	if (opts.showtitle)
		url += '&showtitle=' + opts.showtitle;
	if (opts.sma)
	  url += '&sma=' + encodeURIComponent(opts.sma);
	if (opts.zoom && opts.zoom !== 130)
      url += '&zoom=' + encodeURIComponent(opts.zoom/100);
    if (opts.annotate)
     url += '&annotate=' + encodeURIComponent(opts.annotate);
    if (opts.arw)
      url += '&arw=' + encodeURIComponent(opts.arw);
    if (opts.dat !== 'm')
      url += '&dat=' + encodeURIComponent(opts.dat);
  	if (opts.flip)
    	url += '&f=1';
  	if (opts.rotate)
    	url += '&r=' + encodeURIComponent(opts.rotate);
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
    $outer.append($div);
    return $outer;
}

function handle_img_error(img) {
  $.ajax($(img).attr('src')).error(function(r){
    reason = r.responseText;
    var tempDom = $('<output>').append($.parseHTML(reason));
    console.log($('div', tempDom).html());
    $(img).parent().parent().html($('<div class="error-mesg">').append($('div', tempDom).html()));
    $(img).parent().parent().addClass("error");
  });
}