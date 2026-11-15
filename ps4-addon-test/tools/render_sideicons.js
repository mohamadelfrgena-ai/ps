const { Resvg } = require('@resvg/resvg-js');
const fs = require('fs');
const names = ['home','offers','store','downloads','plans','account','settings'];
// w = white (active on blue tile), d = kMU dark theme {134,169,181}, l = kMU light theme {92,101,114}
const colors = { w: '#ffffff', d: '#86a9b5', l: '#5c6572' };
const outDir = process.argv[2];
for (const n of names) {
  let svg = fs.readFileSync(`/tmp/sideicons/icons/${n}.svg`, 'utf8');
  for (const [tag, hex] of Object.entries(colors)) {
    const colored = svg.replace(/#fff/g, hex);
    const r = new Resvg(colored, { fitTo: { mode: 'width', value: 80 } });
    fs.writeFileSync(`${outDir}/ric_${n}_${tag}.png`, r.render().asPng());
  }
}
console.log('rendered', names.length * 3);
