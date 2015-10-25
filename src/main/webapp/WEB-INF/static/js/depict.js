function update() {
  var input  = $('#input').val();
  var result = $('#result');
  
  $('#result tr').remove();
  
  var opts = {
              'style':    $("select[name='style'] option:selected").val(),
              'annotate': $("select[name='annotate'] option:selected").val(),
              'zoom':     $("input[name='zoom']").val()
              };
                 
  var lines = input.split("\n");
  var current_row = 0;
  var row = $('<tr></tr>');
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i].trim();
    
    // skip empty lines and comments
    if (line.length == 0 || line.charAt(0) === '#')
      continue
          
    var title = line.substring(line.indexOf(' ')+1);
  	
  	current_row++;
  	row.append('<td>' + generate(opts, line, title) + '</td>');
  	
  	if (current_row == 3) {
  	    result.append(row);
  	    current_row = 0;
  	    row = $('<tr></tr>');
  	}
  }
  
  if (current_row > 0) {
    result.append(row);
  }
}

function depict_url(opts, smiles, w, h) {
	var url = 'depict/' + opts.style + '/svg?smi=' + encodeURIComponent(smiles);
	if (w && h)
	  url += '&w=' + w + '&h=' + h;
	if (opts.sma)
	  url += '&sma=' + encodeURIComponent(opts.sma);
	if (opts.zoom)
      url += '&zoom=' + encodeURIComponent(opts.zoom/100);
    if (opts.annotate)
     url += '&annotate=' + encodeURIComponent(opts.annotate);
	return url;    
}

function generate(opts, smiles, title) {
  	return '<table class="grid"><tr><td>' +
  		   	'<a href="' + depict_url(opts, smiles) + '">' +	
  	       	 '<img alt="No Image, Invalid SMILES?" class="chemimg" src="' + depict_url(opts, smiles, 80, 49) + '"/>' +
  	       	'</a>' + 
  	       '</td></tr>' +
  	       '<tr><td class="chemtitle">' + title + '</td></tr></table>';
}