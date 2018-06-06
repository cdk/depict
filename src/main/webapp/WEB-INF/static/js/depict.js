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

  var lines = input.split("\n");

  for (var i = 0; i < lines.length; i++) {
    var line = lines[i].trim();
    
    // skip empty lines and comments
    if (line.length == 0 || line.charAt(0) === '#')
      continue

    var title = line.indexOf(' ') >= 0 ? line.substring(line.indexOf(' ')+1) : '';
    title = title.replace(/^\|[^|]+\|\s+/, "");
    console.log(generate(opts, line, title));
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
                                                       .attr('src', depict_url(opts, smiles, width, height))));
}