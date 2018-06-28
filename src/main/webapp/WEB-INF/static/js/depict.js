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

function update() {
  var input  = $('#input').val();
  var result = $('#result');
  result.empty();

  var opts = {
              'style':     $("select[name='style'] option:selected").val(),
              'annotate':  $("select[name='annotate'] option:selected").val(),
              'zoom':      $("input[name='zoom']").val(),
              'sma':       $("input[name='smarts']").val(),
              'hdisp':     $("select[name='hdisp'] option:selected").val(),
              'showtitle': $("input[name='showtitle']").is(':checked'),
              'abbr':      $("select[name='abbr'] option:selected").val()
              };

  var lines  = input.split("\n");
  var nlines = lines.length < 500 ? lines.length : 500;

  if (lines.length > 500) {
    alert("Only the first 500 structure will be displayed!");
  }

  for (var i = 0; i < nlines; i++) {
    var line = lines[i].trim();
    
    // skip empty lines and comments
    if (line.length == 0 || line.charAt(0) === '#')
      continue

    var title = smittl(line);
    title = title.replace(/^\|[^|]+\|\s+/, "");
    if (!title)
      title = "#" + (1+i);
  	result.append(generate(opts, line, title));
  }
}

function depict_url(opts, smiles, w, h) {
	var smi = encodeURIComponent(smiles);
	var url = './depict/' + opts.style + '/svg?smi=' + smi;
	if (w && h)
	  url += '&w=' + w + '&h=' + h;
	url += '&abbr=' + opts.abbr;
	url += '&hdisp=' + opts.hdisp;
	url += '&showtitle=' + opts.showtitle;
	if (opts.sma)
	  url += '&sma=' + encodeURIComponent(opts.sma);
	if (opts.zoom)
      url += '&zoom=' + encodeURIComponent(opts.zoom/100);
    if (opts.annotate)
     url += '&annotate=' + encodeURIComponent(opts.annotate);
	return url;    
}

function generate(opts, smiles, title) {
    var isrxn  = smiles.indexOf('>') != -1;
    var width  = isrxn ? '210' : '80';
    var height = '50';
    return $('<div>').addClass('chemdiv')
                     .append($('<a>').attr('href', depict_url(opts, smiles))
                                     .append($('<img>').addClass('chemimg')
                                                       .addClass(isrxn ? 'chemrxn' : 'chemmol')
                                                       .attr('src', depict_url(opts, smiles, width, height))))
                     .append($('<div>').append(title));
}