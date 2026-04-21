var ROOT_URL = ".";
var mapUpdateSerial = 0;

function clearMapInput() {
  $('#map_input').val('');
  updateMap();
}

function toggleMapExtraOpts() {
  $('#depict_extra_opts').slideToggle();
}

function smittl(smiles) {
  var i = smiles.indexOf(' ');
  var j = smiles.indexOf('\t');
  if (j >= 0 && (j < i || i < 0))
    i = j;
  if (i < 0)
    return '';
  return smiles.substring(i + 1);
}

function mapSmilesUrl(smiles) {
  return ROOT_URL + '/map/smi?smi=' + encodeURIComponent(smiles);
}

function mapUrl(opts, smiles, fmt, w, h) {
  var smi = encodeURIComponent(smiles);
  var url = ROOT_URL + '/map/' + opts.style + '/' + fmt + '?smi=' + smi;
  if (w && h)
    url += '&w=' + w + '&h=' + h;
  url += '&abbr=' + opts.abbr;
  url += '&hdisp=' + opts.hdisp;
  if (opts.showtitle)
    url += '&showtitle=' + opts.showtitle;
  if (opts.sma)
    url += '&sma=' + encodeURIComponent(opts.sma);
  if (opts.zoom && opts.zoom !== 130)
    url += '&zoom=' + encodeURIComponent(opts.zoom / 100);
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

function renderMapSMILES(inputs, opts, updateId) {
  var result = [];
  var nlines = inputs.length < 500 ? inputs.length : 500;
  if (inputs.length > 500) {
    alert("Only the first 500 entries will be displayed!");
  }

  for (var i = 0; i < nlines; i++) {
    var input = inputs[i].trim();

    if (input.length === 0 || input.charAt(0) === '#')
      continue;

    var title = smittl(input);
    title = title.replace(/^\|[^|]+\|\s+/, "");
    if (!title)
      title = "#" + (1 + i);

    result.push(renderMapResult(opts, input, title, updateId));
  }

  return result;
}

function updateMap() {
  mapUpdateSerial++;
  var updateId = mapUpdateSerial;
  var input = $('#map_input').val();
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

  if (!input)
    return;

  result.append(renderMapSMILES(input.split("\n"), opts, updateId));
}

function renderMapResult(opts, smiles, title, updateId) {
  var $outer = $('<div>').addClass('chemdiv').addClass('scheme');
  var $img = $('<div class="img">').append($('<div class="map-loading">').text('Mapping reaction...'));
  var $div = $('<div class="grid">').append($img);
  var $meta = $('<div class="map-meta">');
  if (!opts.showtitle)
    $meta.append($('<div class="title">').append(title));
  $meta.append($('<label class="map-output-label">').text('Mapped SMILES'));
  var $output = $('<textarea class="map-output" readonly>');
  $meta.append($output);
  $div.append($meta);
  $outer.append($div);

  $.ajax({
    url: mapSmilesUrl(smiles),
    method: 'GET',
    dataType: 'text',
    success: function(mappedSmiles) {
      if (updateId !== mapUpdateSerial)
        return;

      mappedSmiles = (mappedSmiles || '').trim();
      $output.val(mappedSmiles);
      populateMapImage($img, opts, smiles, title);
    },
    error: function(r) {
      if (updateId !== mapUpdateSerial)
        return;
      showMapError($img, r.responseText);
    }
  });

  return $outer;
}

function populateMapImage($img, opts, smiles, title) {
  var svgUrl = mapUrl(opts, smiles, 'svg', -1, -1);
  var pngUrl = mapUrl(opts, smiles, 'png', -1, -1);
  var pdfUrl = mapUrl(opts, smiles, 'pdf', -1, -1);

  $img.empty();
  $img.append('<span class="valign-helper"></span>');
  $img.append($('<a href="' + svgUrl + '">').append($('<img onerror="handleMapImgError(this)">')
    .addClass('chemimg')
    .attr('src', svgUrl)));

  var $links = $("<div class='links'>");
  $links.append('<a title="Download SVG" href="' + svgUrl + '" download="' + title + '.svg"><i class="icon-file-svg icon" aria-hidden="true"></i></a><br>');
  $links.append('<a title="Download PNG" href="' + pngUrl + '" download="' + title + '.png"><i class="icon-file-png icon" aria-hidden="true"></i></a><br>');
  $links.append('<a title="Download PDF" href="' + pdfUrl + '" download="' + title + '.pdf"><i class="icon-file-pdf icon" aria-hidden="true"></i></a>');
  $img.append($links);
}

function extractMapError(reason) {
  if (!reason)
    return 'Unable to map reaction.';

  var tempDom = $('<output>').append($.parseHTML(reason));
  var html = $('div', tempDom).html();
  if (html)
    return html;

  return $('<div>').text(reason).html();
}

function showMapError($img, reason) {
  var message = extractMapError(reason);
  $img.html($('<div class="error-mesg">').append(message));
  $img.addClass("error");
}

function handleMapImgError(img) {
  $.ajax({
    url: $(img).attr('src'),
    method: 'GET',
    success: function(data) {
      showMapError($(img).parent().parent(), data);
    },
    error: function(r) {
      showMapError($(img).parent().parent(), r.responseText);
    }
  });
}
