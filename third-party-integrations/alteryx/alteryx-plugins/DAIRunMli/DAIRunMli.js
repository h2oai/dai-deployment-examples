const displayStandardMliOptions = () => {
    if (Alteryx.Gui.Manager.getDataItem('IsTimeSeries').getValue()) {
        document.getElementById('datasetkey').style.display = 'none'
        document.getElementById('targetcolumn').style.display = 'none'
    } else {
        document.getElementById('datasetkey').style.display = 'initial'
        document.getElementById('targetcolumn').style.display = 'initial'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {
    displayStandardMliOptions()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayStandardMliOptions()
    Alteryx.Gui.Manager.getDataItem('IsTimeSeries').registerPropertyListener('value', displayStandardMliOptions)
}